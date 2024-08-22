package waffleoRai_extractMyu.mains;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import waffleoRai_Executable.elf.ELF;
import waffleoRai_Executable.elf.ELFSection;
import waffleoRai_Executable.elf.ELFSymbol;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_extractMyu.Main;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class Elf2PsxExe {
	
	private static final String MAGIC = "PS-X EXE";
	
	private static final int REG_JP = 0;
	private static final int REG_NA = 1;
	private static final int REG_EU = 2;
	
	private static final String RSTR_JP = "Sony Computer Entertainment Inc. for Japan area";
	private static final String RSTR_NA = "Sony Computer Entertainment Inc. for North America area";
	private static final String RSTR_EU = "Sony Computer Entertainment Inc. for Europe area";
	
	private static final long ADDR_LOAD = 0x80010000L;
	private static final long ADDR_STACK = 0x801ffff0L;
	
	private String inDir;
	
	private String inPath;
	private String outPath;
	private int region = REG_JP;
	
	private static class SecNode{
		public ELFSection section;
		public SecNode next;
	}
	
	private static boolean checkArgs(Map<String, String> argmap, Elf2PsxExe ctx) throws IOException {
		ctx.inPath = argmap.get("input");
		ctx.outPath = argmap.get("output");
		if(argmap.containsKey("na")) ctx.region = REG_NA;
		if(argmap.containsKey("eu")) ctx.region = REG_EU;
		
		if(ctx.inPath == null) {
			MyuPackagerLogger.logMessage("Elf2PsxExe.checkArgs", "Input ELF file is required!");
			return false;
		}
		if(!FileBuffer.fileExists(ctx.inPath)) {
			MyuPackagerLogger.logMessage("Elf2PsxExe.checkArgs", "Input  \"" + ctx.inPath + "\" does not exist!");
			return false;
		}
		
		if(ctx.inPath.contains(File.separator)) {
			ctx.inDir = ctx.inPath.substring(0, ctx.inPath.lastIndexOf(File.separator));
		}
		else ctx.inDir = ".";
		
		if(ctx.outPath == null) {
			ctx.outPath = ctx.inDir + File.separator + "myubuild.psxexe";
			MyuPackagerLogger.logMessage("Elf2PsxExe.checkArgs", "Output path not specified. Set to: " + ctx.outPath);
		}
		
		if(ctx.outPath.contains(File.separator)) {
			String outdir = ctx.outPath.substring(0, ctx.outPath.lastIndexOf(File.separator));
			if(!FileBuffer.directoryExists(outdir)) {
				Files.createDirectories(Paths.get(outdir));
			}
		}
		
		return true;
	}
	
	public static void main_elf2psxexe(Map<String, String> argmap) throws IOException, UnsupportedFileTypeException {
		Elf2PsxExe ctx = new Elf2PsxExe();
		if(!checkArgs(argmap, ctx)) {
			MyuPackagerLogger.logMessage("Elf2PsxExe.main_elf2psxexe", "Arg check failed!");
			Main.printUsage_elf2psxexe();
			System.exit(1);
		}
		
		//Load ELF
		FileBuffer elfLoad = FileBuffer.createBuffer(ctx.inPath, false);
		ELF elf = ELF.read(elfLoad);
		
		//Try to get entry point either from ELF or by looking for a symbol called "_entry". Otherwise default to text + 8
		long entryAddr = elf.getEntryAddress();
		if(entryAddr < 1L) {
			MyuPackagerLogger.logMessage("Elf2PsxExe.main_elf2psxexe", "Entry address not specified in ELF. Looking for entry symbol...");
			List<ELFSymbol> stable = elf.getSymbolTable();
			if(stable != null && !stable.isEmpty()) {
				for(ELFSymbol sym : stable) {
					String sname = sym.getName();
					if(sname.equals("_entry")) {
						entryAddr = sym.getAddress();
						break;
					}
				}
			}
			else {
				MyuPackagerLogger.logMessage("Elf2PsxExe.main_elf2psxexe", "Input ELF does not have a symbol table!");
			}
		}
		
		ELFSection s_text = elf.getSectionByName(".text");
		if(s_text == null) {
			MyuPackagerLogger.logMessage("Elf2PsxExe.main_elf2psxexe", "Input ELF does not have a .text section!");
			System.exit(1);
		}
		
		if(entryAddr < 1L) {
			MyuPackagerLogger.logMessage("Elf2PsxExe.main_elf2psxexe", "Entry address defaulting to text start + 8");
			entryAddr = s_text.getVirtualAddr() + 8L;
		}
		
		MyuPackagerLogger.logMessage("Elf2PsxExe.main_elf2psxexe", "Entry address set to " + String.format("0x%08x", entryAddr));
		
		ELFSection s_data = elf.getSectionByName(".data");
		ELFSection s_rodata = elf.getSectionByName(".rodata");
		ELFSection s_sdata = elf.getSectionByName(".sdata");
		
		//Determine file size
		long v_end = s_text.getVirtualAddr() + s_text.getROMSize();
		if(s_data != null) {
			long v_end_sec = s_data.getVirtualAddr() + s_data.getROMSize();
			if(v_end_sec > v_end) v_end = v_end_sec;
		}
		if(s_rodata != null) {
			long v_end_sec = s_rodata.getVirtualAddr() + s_rodata.getROMSize();
			if(v_end_sec > v_end) v_end = v_end_sec;
		}
		if(s_sdata != null) {
			long v_end_sec = s_sdata.getVirtualAddr() + s_sdata.getROMSize();
			if(v_end_sec > v_end) v_end = v_end_sec;
		}
		
		//Round up to next CD sector
		int contentSize = (int)(v_end - ADDR_LOAD);
		contentSize = (contentSize + 0x7ff) & ~0x7ff;
		
		//Generate header.
		FileBuffer header = new FileBuffer(0x800, false);
		header.printASCIIToFile(MAGIC);
		header.addToFile(0L); //Padding
		header.addToFile((int)entryAddr);
		header.addToFile(0); //$gp - Typically unused in retail versions (set during entry)
		header.addToFile((int)ADDR_LOAD);
		header.addToFile(contentSize);
		for(int i = 0; i < 0x10; i++) header.addToFile(FileBuffer.ZERO_BYTE); //Unknown or unused
		header.addToFile((int)ADDR_STACK);
		header.addToFile(0);
		for(int i = 0; i < 0x14; i++) header.addToFile(FileBuffer.ZERO_BYTE); //System use
		
		//Print region-specific ASCII string
		switch(ctx.region) {
		case REG_NA:
			header.printASCIIToFile(RSTR_NA);
			break;
		case REG_EU:
			header.printASCIIToFile(RSTR_EU);
			break;
		case REG_JP:
		default:
			header.printASCIIToFile(RSTR_JP);
			break;
		}
		while(header.getFileSize() < 0x800) header.addToFile(FileBuffer.ZERO_BYTE);
		
		//Sort sections
		SecNode head = new SecNode();
		head.section = s_text;
		if(s_data != null) {
			SecNode mynode = new SecNode();
			mynode.section = s_data;
			if(s_data.getVirtualAddr() > s_text.getVirtualAddr()) head.next = mynode;
			else {
				mynode.next = head;
				head = mynode;
			}
		}
		if(s_rodata != null) {
			SecNode mynode = new SecNode();
			mynode.section = s_rodata;
			SecNode nn = head;
			boolean tail = false;
			while(nn.section.getVirtualAddr() < mynode.section.getVirtualAddr()) {
				if(nn.next == null) {
					tail = true;
					break;
				}
				nn = nn.next;
			}
			if(tail) nn.next = mynode;
			else {
				mynode.next = nn;
				if(nn == head) head = mynode;
			}
		}
		if(s_sdata != null) {
			SecNode mynode = new SecNode();
			mynode.section = s_sdata;
			SecNode nn = head;
			boolean tail = false;
			while(nn.section.getVirtualAddr() < mynode.section.getVirtualAddr()) {
				if(nn.next == null) {
					tail = true;
					break;
				}
				nn = nn.next;
			}
			if(tail) nn.next = mynode;
			else {
				mynode.next = nn;
				if(nn == head) head = mynode;
			}
		}
		
		long caddr = ADDR_LOAD;
		FileBuffer contents = new FileBuffer(contentSize, false);
		while(head != null) {
			long saddr = head.section.getVirtualAddr();
			while(caddr < saddr) {
				contents.addToFile(FileBuffer.ZERO_BYTE);
				caddr++;
			}
			
			byte[] sdat = head.section.getRawData();
			for(int i = 0; i < sdat.length; i++) {
				contents.addToFile(sdat[i]);
				caddr++;
			}
			
			head = head.next;
		}
		while(contents.getFileSize() < contentSize) contents.addToFile(FileBuffer.ZERO_BYTE);
		
		//Output file
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(ctx.outPath));
		header.writeToStream(bos);
		contents.writeToStream(bos);
		bos.close();
		
	}

}
