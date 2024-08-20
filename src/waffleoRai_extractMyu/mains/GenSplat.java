package waffleoRai_extractMyu.mains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileUtils;
import waffleoRai_extractMyu.Main;
import waffleoRai_extractMyu.MyuCode;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.tables.Section;
import waffleoRai_extractMyu.tables.SectionTable;

public class GenSplat {
	
	private String secTblPath;
	private String exePath;
	private String opsPath;
	private String outPath;
	private String buildDirRel;
	
	private static boolean checkArgs(Map<String, String> argmap, GenSplat ctx) throws IOException {
		ctx.secTblPath = argmap.get("sectbl");
		ctx.exePath = argmap.get("exepath");
		ctx.opsPath = argmap.get("ops");
		ctx.outPath = argmap.get("out");
		ctx.buildDirRel = argmap.get("bdir");
		
		if(ctx.secTblPath == null) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "Section table path is required!");
			return false;
		}
		
		if(!FileBuffer.fileExists(ctx.secTblPath)) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "Provided section table file \"" + ctx.secTblPath + "\" does not exist!");
			return false;
		}
		
		if(ctx.opsPath == null) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "Options yaml path is required!");
			return false;
		}
		
		if(!FileBuffer.fileExists(ctx.opsPath)) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "Provided options yaml file \"" + ctx.opsPath + "\" does not exist!");
			return false;
		}
		
		if(ctx.outPath == null) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "WARNING: Output path was not provided.");
			ctx.outPath = ctx.opsPath.substring(0, ctx.opsPath.lastIndexOf(File.separator)) + File.separator + "splat.yaml";
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "Output set to \"" + ctx.outPath + "\"");
		}
		
		if(ctx.exePath == null) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "WARNING: PSXEXE path not provided. Defaulting to ${OUTPUT_DIR}/cd/SLPM_871.78");
			ctx.outPath = ctx.outPath.substring(0, ctx.outPath.lastIndexOf(File.separator)) + File.separator + "cd" + File.separator + "SLPM_871.78";
		}
		
		if(!FileBuffer.fileExists(ctx.exePath)) {
			MyuPackagerLogger.logMessage("GenSplat.checkArgs", "Provided PSXEXE file \"" + ctx.exePath + "\" does not exist!");
			return false;
		}
		
		if(ctx.buildDirRel == null) ctx.buildDirRel = "cd";
		
		return true;
	}

	public static void main_genSplat(Map<String, String> argmap) throws IOException {
		GenSplat ctx = new GenSplat();
		if(!checkArgs(argmap, ctx)) {
			Main.printUsage_SplatYaml();
			System.exit(1);
		}
		
		//Read section table
		List<Section> seclist = SectionTable.readSectionTableTSV(ctx.secTblPath);
		
		//Hash exe file
		String hashstr = null;
		try {
			FileBuffer exedata = FileBuffer.createBuffer(ctx.exePath, false);
			byte[] hash = FileUtils.getSHA1Hash(exedata.getBytes());
			exedata.dispose();
			hashstr = FileUtils.bytes2str(hash);
		}catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		//Read options (Mostly just copies it over)
		List<String> oplines = new LinkedList<String>();
		BufferedReader br = new BufferedReader(new FileReader(ctx.opsPath));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.startsWith("options:")) continue;
			oplines.add(line);
		}
		br.close();
		
		//Output to new yaml
		String exefn = ctx.exePath.substring(ctx.exePath.lastIndexOf(File.separator) + 1);
		BufferedWriter bw = new BufferedWriter(new FileWriter(ctx.outPath));
		bw.write("name: " + exefn + "\n");
		bw.write("sha1: " + hashstr + "\n");
		bw.write("options:\n");
		bw.write("  basename: " + exefn.toLowerCase() + "\n");
		bw.write("  target_path: " + ctx.buildDirRel + "/" + exefn.toLowerCase() + "\n");
		for(String opline : oplines) {
			bw.write(opline + "\n");
		}
		
		bw.write("segments:\n");
		//TBH just gonna regen the header anyway but eh
		bw.write("  - name: header\n");
		bw.write("    type: header\n");
		bw.write("    start: 0x0\n\n");
		
		bw.write("  - name: main\n");
		bw.write("    type: code\n");
		bw.write("    start: 0x800\n");
		bw.write("    vram: 0x80010000\n");
		bw.write("    bss_size: 0x0\n");
		bw.write("    subalign: 4\n");
		
		bw.write("    subsegments:\n");
		
		//ROData
		for(Section sec : seclist) {
			String secname = sec.getName();
			if(secname.equals("END") && !sec.isSys()) break;
			if(sec.hasROData()) {
				bw.write("      - [0x");
				int offset = MyuCode.address2ExeOffset(sec.getRODataAddr());
				bw.write(Integer.toHexString(offset));
				bw.write(", rodata, ");
				
				String libname = sec.getLibName();
				if(sec.isSys()) {
					bw.write("psx/");
				}
				if(libname != null && !libname.isEmpty()) bw.write(libname + "/");
				bw.write(secname);
				bw.write("]\n");
			}
		}
		
		//Text
		for(Section sec : seclist) {
			String secname = sec.getName();
			if(secname.equals("END") && !sec.isSys()) break;
			if(sec.hasText()) {
				bw.write("      - [0x");
				int offset = MyuCode.address2ExeOffset(sec.getTextAddr());
				bw.write(Integer.toHexString(offset));
				bw.write(", asm, text/");
				
				String libname = sec.getLibName();
				if(sec.isSys()) {
					bw.write("psx/");
				}
				if(libname != null && !libname.isEmpty()) bw.write(libname + "/");
				bw.write(secname);
				bw.write("]\n");
			}
		}
		
		//Data
		for(Section sec : seclist) {
			String secname = sec.getName();
			if(secname.equals("END") && !sec.isSys()) {
				//System.err.println("Data End VAddr: " + String.format("%08x", sec.getDataAddr()));
				//int offset = MyuCode.address2ExeOffset(sec.getDataAddr());
				//bw.write(String.format("  - [0x%x]\n", offset));
				break;
			}
			if(sec.hasData()) {
				bw.write("      - [0x");
				int offset = MyuCode.address2ExeOffset(sec.getDataAddr());
				bw.write(Integer.toHexString(offset));
				bw.write(", data, ");
				
				String libname = sec.getLibName();
				if(sec.isSys()) {
					bw.write("psx/");
				}
				if(libname != null && !libname.isEmpty()) bw.write(libname + "/");
				bw.write(secname);
				bw.write("]\n");
			}
		}
		
		//sdata
		for(Section sec : seclist) {
			String secname = sec.getName();
			if(secname.equals("END") && !sec.isSys()) {
				//System.err.println("Data End VAddr: " + String.format("%08x", sec.getDataAddr()));
				int offset = MyuCode.address2ExeOffset(sec.getSDataAddr());
				bw.write(String.format("  - [0x%x]\n", offset));
				break;
			}
			if(sec.hasSData()) {
				bw.write("      - [0x");
				int offset = MyuCode.address2ExeOffset(sec.getSDataAddr());
				bw.write(Integer.toHexString(offset));
				bw.write(", sdata, ");
				
				String libname = sec.getLibName();
				if(sec.isSys()) {
					bw.write("psx/");
				}
				if(libname != null && !libname.isEmpty()) bw.write(libname + "/");
				bw.write(secname);
				bw.write("]\n");
			}
		}
		
		bw.close();
		
	}
	
}
