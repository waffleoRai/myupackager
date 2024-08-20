package waffleoRai_extractMyu.mains;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import waffleoRai_Executable.elf.ELF;
import waffleoRai_Executable.elf.ELFReloc;
import waffleoRai_Executable.elf.ELFSection;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.MyuCode;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.tables.Section;
import waffleoRai_extractMyu.tables.SectionTable;

public class ObjCheck {
	
	private String indir;
	
	private String objDir;
	private String exePath;
	private String sectblPath;
	
	private static boolean checkArgs(Map<String, String> argmap, ObjCheck ctx) throws IOException {
		ctx.sectblPath = argmap.get("sectbl");
		ctx.exePath = argmap.get("exefile");
		ctx.objDir = argmap.get("builddir");
		
		if(ctx.sectblPath == null) {
			MyuPackagerLogger.logMessage("ObjCheck.checkArgs", "Input section table is required!");
			return false;
		}
		if(!FileBuffer.fileExists(ctx.sectblPath)) {
			MyuPackagerLogger.logMessage("ObjCheck.checkArgs", "Input section table \"" + ctx.sectblPath + "\" does not exist!");
			return false;
		}
		
		if(ctx.exePath == null) {
			MyuPackagerLogger.logMessage("ObjCheck.checkArgs", "Input PSXEXE is required!");
			return false;
		}
		if(!FileBuffer.fileExists(ctx.exePath)) {
			MyuPackagerLogger.logMessage("ObjCheck.checkArgs", "Input PSXEXE \"" + ctx.exePath + "\" does not exist!");
			return false;
		}
		
		String[] spl = ctx.sectblPath.split(File.separator);
		for(int i = 0; i <= (spl.length - 2); i++) {
			if(i > 0) ctx.indir += File.separator;
			ctx.indir += spl[i];
		}
		
		if(ctx.objDir == null) {
			ctx.objDir = ctx.indir + File.separator + "build" + File.separator + "obj";
			MyuPackagerLogger.logMessage("ObjCheck.checkArgs", "Object dir not provided. Set to: " + ctx.objDir);
		}
		
		return true;
	}
	
	public static void main_objcheck(Map<String, String> argmap) throws IOException, UnsupportedFileTypeException {
		ObjCheck ctx = new ObjCheck();
		if(!checkArgs(argmap, ctx)) {
			System.exit(1);
		}
		
		List<Section> sectbl = SectionTable.readSectionTableTSV(ctx.sectblPath);
		if(sectbl == null || sectbl.isEmpty()) {
			MyuPackagerLogger.logMessage("ObjCheck.main_objcheck", "Section table could not be read!");
			System.exit(1);
		}
		
		FileBuffer exedat = FileBuffer.createBuffer(ctx.exePath, false);
		final String snames[] = {".rodata", ".text", ".data", ".sdata", ".sbss", ".bss"};
		
		for(Section sec : sectbl) {
			//Load obj
			String opath = ctx.objDir + File.separator;
			if(sec.isSys()) opath += "psx" + File.separator;
			String libname = sec.getLibName();
			if(libname != null) opath += libname + File.separator;
			opath += sec.getName() + ".o";
			String fullname = sec.getName();
			if(libname != null) fullname = libname + "::" + fullname;
			
			if(!FileBuffer.fileExists(opath)) {
				System.out.println(fullname + ": [X]");
				System.out.println("\t" + opath + " could not be found!");
				continue;
			}
			ELF obj = ELF.read(FileBuffer.createBuffer(opath, false));
			
			//Go through each subsection
			boolean mismatchFound = false;
			for(String sname : snames) {
				//get exe position from sectbl
				long staddr = 0;
				int ssize = 0;
				if(sname.equals(".rodata")) {
					staddr = sec.getRODataAddr();
					ssize = sec.getRODataSize();
				}
				else if(sname.equals(".text")) {
					staddr = sec.getTextAddr();
					ssize = sec.getTextSize();
				}
				else if(sname.equals(".data")) {
					staddr = sec.getDataAddr();
					ssize = sec.getDataSize();
				}
				else if(sname.equals(".sdata")) {
					staddr = sec.getSDataAddr();
					ssize = sec.getSDataSize();
				}
				else if(sname.equals(".sbss")) {
					staddr = sec.getSBssAddr();
					ssize = sec.getSBssSize();
				}
				else if(sname.equals(".bss")) {
					staddr = sec.getBssAddr();
					ssize = sec.getBssSize();
				}
				
				if(staddr < 1L || ssize < 1) continue;
				long stoff = MyuCode.address2ExeOffset(staddr);
				
				ELFSection osec = obj.getSectionByName(sname);
				FileBuffer refsec = exedat.createCopy(stoff, stoff + ssize);
				
				//Zero out any relocations
				List<ELFReloc> relocs = obj.getRelocTableForSection(sname);
				if(relocs != null && !relocs.isEmpty()) {
					for(ELFReloc reloc : relocs) {
						long rofs = reloc.getAddress();
						int rtype = reloc.getType();
						
						switch(rtype) {
						case MyuCode.MIPS_RELOC_26:
							int word = refsec.intFromFile(rofs);
							word &= ~0x03ffffff;
							refsec.replaceInt(word, rofs);
							break;
						case MyuCode.MIPS_RELOC_GPREL16:
						case MyuCode.MIPS_RELOC_HI16:
						case MyuCode.MIPS_RELOC_LO16:
							refsec.replaceShort((short)0, rofs);
							break;
						case MyuCode.MIPS_RELOC_32:
							refsec.replaceInt(0, rofs);
							break;
						default:
							MyuPackagerLogger.logMessage("ObjCheck.main_objcheck", "ERROR: Unknown reloc type: " + rtype);
							break;
						}
					}
				}

				//Compare. If mismatch flag and print
				byte[] odat = osec.getRawData();
				int mismatchPos = -1;
				for(int i = 0; i < ssize; i++) {
					if(i >= odat.length) {
						mismatchPos = i;
						break;
					}
					byte rb = refsec.getByte(i);
					if(rb != odat[i]) {
						mismatchPos = i;
						break;
					}
				}
				refsec.dispose();
				
				if(mismatchPos >= 0) {
					if(!mismatchFound) {
						//First mismatch.
						System.out.println(fullname + ": [X]");
					}
					
					long mismatchAddr = staddr + mismatchPos;
					System.out.println(String.format("\t%s @ 0x%08x", sname, mismatchAddr));
					mismatchFound = true;
				}
			}
			
			//Print results
			if(!mismatchFound) System.out.println(fullname + ": [OK]");
		}
		
	}

}
