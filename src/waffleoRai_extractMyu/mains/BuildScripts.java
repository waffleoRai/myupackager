package waffleoRai_extractMyu.mains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.LiteNode;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class BuildScripts {
	
	private static final String SHVAR_SMERGE = "SMERGE_SH";
	private static final String SHVAR_PSYQ_DIR = "PSYQ_DIR";
	private static final String SHVAR_WIN_TOOL = "WIN_TOOL";
	private static final String SHVAR_INCL_DIR = "INCL_DIR";
	private static final String SHVAR_MASPSX = "MASPSX_PATH";
	private static final String SHVAR_MYUJAR = "MYUJAR";
	private static final String SHVAR_MYUPACK = "MYUPACK";
	
	private static final String ADD_ASM_ARGS = "-mips1 -msoft-float";
	private static final String ADD_GCC_ARGS = "-msoft-float -mlong32 -finline -fpeephole";
	private static final String ADD_MASPSX_ARGS = "--aspsx-version=2.86 --dont-force-G0";
	
	private static class BuildModule{
		public String name;
		public String libName;
		
		public String oPath;
		public String oPathRel;
		public boolean useAsm;
		
		public String roDataPath;
		public String textPath;
		public String dataPath;
		public String bssPath;
		public String sdataPath;
		public String sbssPath;
		
		public String cPath;
		public String cOptLevel;
		public String inclDirs;
		public boolean asmAdj = false;
	}
	
	private String xmlPath;
	private String shPath;
	private String ldPath;
	private String objPath;
	
	private String indir;
	private String buildDir;
	private boolean gnuFlag = false;
	private boolean wslFlag = false;
	private boolean bsscomm = false;
	
	private ArrayList<BuildModule> modules;
	
	private static boolean checkArgs(Map<String, String> argmap, BuildScripts ctx) throws IOException {
		ctx.xmlPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("xmlspec"));
		ctx.shPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("shout"));
		ctx.ldPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("ldout"));
		ctx.objPath = MyuArcCommon.getSystemAbsolutePath(argmap.get("binout"));
		ctx.buildDir = MyuArcCommon.getSystemAbsolutePath(argmap.get("builddir"));
		ctx.gnuFlag = argmap.containsKey("gnu");
		ctx.wslFlag = argmap.containsKey("wsl");
		ctx.bsscomm = argmap.containsKey("bsscomm");
		
		if(ctx.xmlPath == null) {
			MyuPackagerLogger.logMessage("BuildScripts.checkArgs", "Input XML is required!");
			return false;
		}
		if(!FileBuffer.fileExists(ctx.xmlPath)) {
			MyuPackagerLogger.logMessage("BuildScripts.checkArgs", "Input \"" + ctx.xmlPath + "\" does not exist!");
			return false;
		}
		
		ctx.indir = MyuArcCommon.getContainingDir(ctx.xmlPath);
		
		if(ctx.buildDir == null) {
			ctx.buildDir = ctx.indir + File.separator + "build";
			MyuPackagerLogger.logMessage("BuildScripts.checkArgs", "Build Dir path not provided. Set to: " + ctx.buildDir);
		}
		if(!FileBuffer.directoryExists(ctx.buildDir)) {
			Files.createDirectories(Paths.get(ctx.buildDir));
		}
		
		if(ctx.shPath == null) {
			ctx.shPath = ctx.indir + File.separator + "myubuild.sh";
			MyuPackagerLogger.logMessage("BuildScripts.checkArgs", "Output shell script path not provided. Set to: " + ctx.shPath);
		}
		if(ctx.ldPath == null) {
			ctx.ldPath = ctx.indir + File.separator + "myubuild.ld";
			MyuPackagerLogger.logMessage("BuildScripts.checkArgs", "Output linker script path not provided. Set to: " + ctx.ldPath);
		}
		if(ctx.objPath == null) {
			ctx.objPath = ctx.buildDir + File.separator + "myubuild";
			if(ctx.gnuFlag) ctx.objPath += ".o";
			else {
				ctx.objPath = ctx.objPath.toUpperCase();
				ctx.objPath += ".OBJ";
			}
			MyuPackagerLogger.logMessage("BuildScripts.checkArgs", "Output bin path not provided. Set to: " + ctx.objPath);
		}
		
		
		return true;
	}
	
	private void processModuleBuild(BuildModule moduleSpec, Writer shOut) throws IOException {

		//Look for assembly spec. If not there, look for compile spec.
		String asmdir = buildDir + File.separator + "asm";
		if(moduleSpec.libName != null) asmdir += File.separator + moduleSpec.libName;
		if(!FileBuffer.directoryExists(asmdir)) {
			Files.createDirectories(Paths.get(asmdir));
		}
		
		String spath = asmdir + File.separator + moduleSpec.name + ".s";
		String spathRel = MyuArcCommon.localPath2UnixRel(indir, spath);
		
		if(moduleSpec.useAsm) {
			//If more than one section, merge into file.
			shOut.write("\"${" + SHVAR_SMERGE +"}\"");
			shOut.write(" -o \"" + spathRel + "\"");
			
			//These paths are already relative to xml
			if(moduleSpec.roDataPath != null) shOut.write(" --rodata \"" + moduleSpec.roDataPath + "\"");
			if(moduleSpec.textPath != null) shOut.write(" --text \"" + moduleSpec.textPath + "\"");
			if(moduleSpec.dataPath != null) shOut.write(" --data \"" + moduleSpec.dataPath + "\"");
			if(moduleSpec.sdataPath != null) shOut.write(" --sdata \"" + moduleSpec.sdataPath + "\"");
			if(moduleSpec.sbssPath != null) shOut.write(" --sbss \"" + moduleSpec.sbssPath + "\"");
			if(moduleSpec.bssPath != null) shOut.write(" --bss \"" + moduleSpec.bssPath + "\"");
			shOut.write(" -O\n");
			
			shOut.write("echo -e \"> Assembling "+ spathRel + "...\"\n");
			
			//Assemble command
			if(gnuFlag) {
				shOut.write("mips-linux-gnu-as -EL -O0 -march=r3000 -no-pad-sections");
				shOut.write(" " + ADD_ASM_ARGS);
				shOut.write(" -I \"${" + SHVAR_INCL_DIR + "}\"");
				shOut.write(" -o \"" + moduleSpec.oPathRel + "\"");
				shOut.write(" \"" + spathRel + "\"\n");
			}
			else {
				//TODO No idea what the right params are :)
				if(!wslFlag) {
					shOut.write("${" + SHVAR_WIN_TOOL + "}");
					shOut.write(" ${" + SHVAR_PSYQ_DIR + "}/ASPSX.EXE");
				}
				else {
					shOut.write("\"${" + SHVAR_PSYQ_DIR + "}/ASPSX.EXE\"");
				}
				shOut.write(" -EL -O0 -march=r3000 " + ADD_ASM_ARGS);
				shOut.write(" -I \"${" + SHVAR_INCL_DIR + "}\"");
				shOut.write(" -o \"" + moduleSpec.oPathRel + "\"");
				shOut.write(" \"" + spathRel + "\"\n"); //TODO Script would also have to be cleaned up (eg. remove comments) for ASPSX to even look at it. Also need windows line endings
			}
			
			shOut.write("rm \"" + spathRel + "\"\n\n");
		}
		else {
			shOut.write("echo -e \"> Compiling "+ moduleSpec.cPath + "...\"\n");
			if(!wslFlag) {
				shOut.write("\"${" + SHVAR_WIN_TOOL + "}\" ");
			}
			shOut.write("\"${" + SHVAR_PSYQ_DIR + "}/CPPPSX.EXE\"");
			shOut.write(" -I \"${" + SHVAR_INCL_DIR + "}\"");
			shOut.write(" \"" + moduleSpec.cPath + "\"");
			shOut.write(" | ");
			if(!wslFlag) {
				shOut.write("\"${" + SHVAR_WIN_TOOL + "}\" ");
			}
			shOut.write("\"${" + SHVAR_PSYQ_DIR + "}/CC1PSX.EXE\" -G8");
			shOut.write(" -" + moduleSpec.cOptLevel);
			shOut.write(" -o \"" + spathRel + "\" 2>/dev/null\n");
			
			if(moduleSpec.asmAdj) {
				//Couldn't figure out how to make compiler put things in the right order for INCLUDE_ASM
				//So I have a cheese where I put all INCLUDE_ASM after the last defined function in a dummy function.
				//So then I have to remove the last jr $ra for that dummy function
				//Credit for the awk: https://stackoverflow.com/questions/23696871/how-to-remove-only-the-first-occurrence-of-a-line-in-a-file-using-sed
				
				//shOut.write("tac \"" + spathRel + "\" | awk '!/j*31/ || f++' | tac > \"" + spathRel + ".tmp\"\n");
				shOut.write("${" + SHVAR_MYUPACK + "} rmjrra --input \"" + spathRel + "\" --output \"" + spathRel + ".tmp\"\n");
				shOut.write("mv \"" + spathRel + ".tmp\" \"" + spathRel + "\"\n");
			}
			
			//maspsx
			shOut.write("python3 ${" + SHVAR_MASPSX + "} " + ADD_MASPSX_ARGS + " \"" + spathRel + "\" > \"" + spathRel + ".tmp\"\n");
			shOut.write("mv \"" + spathRel + ".tmp\" \"" + spathRel + "\"\n");
			
			shOut.write("mips-linux-gnu-as -EL -O1 -march=r3000 -no-pad-sections -G8");
			shOut.write(" " + ADD_ASM_ARGS);
			shOut.write(" -I \"${" + SHVAR_INCL_DIR + "}\"");
			shOut.write(" -o \"" + moduleSpec.oPathRel + "\"");
			shOut.write(" \"" + spathRel + "\"\n");
			
			shOut.write("rm \"" + spathRel + "\"\n\n");
		}
		
	}
	
	private void writeLinkerScript() throws IOException {
		//String indent1 = "    ";
		//TODO Need to include the compilation targets
		
		//Similar to splat script, but no PSX header and uses the combined modules
		BufferedWriter bw = new BufferedWriter(new FileWriter(ldPath));
		bw.write("SECTIONS\n{\n");
		bw.write("\t__romPos = 0x800;\n");
		bw.write("\t_gp = ADDR(.sdata);\n");
		bw.write("\tstart = ADDR(.text) + 8;\n");
		
		//rodata
		bw.write("\n\trodata_ROM_START = __romPos;\n");
		bw.write("\trodata_VRAM = ADDR(.rodata);\n");
		bw.write("\t.rodata 0x80010000 : AT(rodata_ROM_START) SUBALIGN(4)\n");
		bw.write("\t{\n");
		bw.write("\t\trodata_START = .;\n");
		for(BuildModule mod : modules) {
			if(mod.roDataPath != null) {
				bw.write("\t\t" + mod.oPathRel + "(.rodata);\n");
			}
			else {
				if(!mod.useAsm) {
					bw.write("\t\t" + mod.oPathRel + "(.rodata);\n");
				}
			}
		}
		bw.write("\t\trodata_END = .;\n");
		bw.write("\t\trodata_SIZE = ABSOLUTE(rodata_END - rodata_START);\n");
		bw.write("\t}\n");
		bw.write("\t__romPos += SIZEOF(.rodata);\n");
		bw.write("\t__romPos = ALIGN(__romPos, 4);\n");
		bw.write("\t. = ALIGN(., 4);\n");
		bw.write("\trodata_ROM_END = __romPos;\n");
		bw.write("\trodata_VRAM_END = .;\n");
		
		//text
		bw.write("\n\ttext_ROM_START = __romPos;\n");
		bw.write("\ttext_VRAM = ADDR(.text);\n");
		bw.write("\t.text rodata_VRAM_END : AT(text_ROM_START) SUBALIGN(4)\n");
		bw.write("\t{\n");
		bw.write("\t\ttext_START = .;\n");
		for(BuildModule mod : modules) {
			if(mod.textPath != null) {
				bw.write("\t\t" + mod.oPathRel + "(.text);\n");
			}
			else {
				if(!mod.useAsm) {
					bw.write("\t\t" + mod.oPathRel + "(.text);\n");
				}
			}
		}
		bw.write("\t\ttext_END = .;\n");
		bw.write("\t\ttext_SIZE = ABSOLUTE(text_END - text_START);\n");
		bw.write("\t}\n");
		bw.write("\t__romPos += SIZEOF(.text);\n");
		bw.write("\t__romPos = ALIGN(__romPos, 4);\n");
		bw.write("\t. = ALIGN(., 4);\n");
		bw.write("\ttext_ROM_END = __romPos;\n");
		bw.write("\ttext_VRAM_END = .;\n");
		
		//data
		bw.write("\n\tdata_ROM_START = __romPos;\n");
		bw.write("\tdata_VRAM = ADDR(.data);\n");
		bw.write("\t.data text_VRAM_END : AT(data_ROM_START) SUBALIGN(4)\n");
		bw.write("\t{\n");
		bw.write("\t\tdata_START = .;\n");
		for(BuildModule mod : modules) {
			if(mod.dataPath != null) {
				bw.write("\t\t" + mod.name + "_data = .;\n");
				bw.write("\t\t" + mod.oPathRel + "(.data);\n");
			}
			else {
				if(!mod.useAsm) {
					bw.write("\t\t" + mod.name + "_data = .;\n");
					bw.write("\t\t" + mod.oPathRel + "(.data);\n");
				}
			}
		}
		bw.write("\t\tdata_END = .;\n");
		bw.write("\t\tdata_SIZE = ABSOLUTE(data_END - data_START);\n");
		bw.write("\t}\n");
		bw.write("\t__romPos += SIZEOF(.data);\n");
		bw.write("\t__romPos = ALIGN(__romPos, 4);\n");
		bw.write("\t. = ALIGN(., 4);\n");
		bw.write("\tdata_ROM_END = __romPos;\n");
		bw.write("\tdata_VRAM_END = .;\n");
		
		//sdata
		bw.write("\n\tsdata_ROM_START = __romPos;\n");
		bw.write("\tsdata_VRAM = ADDR(.sdata);\n");
		bw.write("\t.sdata data_VRAM_END : AT(sdata_ROM_START) SUBALIGN(4)\n");
		bw.write("\t{\n");
		bw.write("\t\tsdata_START = .;\n");
		for(BuildModule mod : modules) {
			if(mod.sdataPath != null) {
				bw.write("\t\t" + mod.name + "_sdata = .;\n");
				bw.write("\t\t" + mod.oPathRel + "(.sdata);\n");
			}
			else {
				if(!mod.useAsm) {
					bw.write("\t\t" + mod.name + "_sdata = .;\n");
					bw.write("\t\t" + mod.oPathRel + "(.sdata);\n");
				}
			}
		}
		bw.write("\t\tsdata_END = .;\n");
		bw.write("\t\tsdata_SIZE = ABSOLUTE(sdata_END - sdata_START);\n");
		bw.write("\t}\n");
		bw.write("\t__romPos += SIZEOF(.sdata);\n");
		bw.write("\t__romPos = ALIGN(__romPos, 4);\n");
		bw.write("\t. = ALIGN(., 4);\n");
		bw.write("\tsdata_ROM_END = __romPos;\n");
		bw.write("\tsdata_VRAM_END = .;\n");
		
		//sbss
		bw.write("\n\tsbss_ROM_START = __romPos;\n");
		bw.write("\tsbss_VRAM = ADDR(.sbss);\n");
		bw.write("\t.sbss sdata_VRAM_END : AT(sbss_ROM_START) SUBALIGN(4)\n");
		bw.write("\t{\n");
		bw.write("\t\tsbss_START = .;\n");
		for(BuildModule mod : modules) {
			if(mod.sbssPath != null) {
				//bw.write("\t\t" + mod.name + "_sbss = .;\n");
				bw.write("\t\t" + mod.oPathRel + "(.sbss);\n");
			}
			else {
				if(!mod.useAsm) {
					bw.write("\t\t" + mod.oPathRel + "(.sbss);\n");
				}
			}
		}
		bw.write("\t\tsbss_END = .;\n");
		bw.write("\t\tsbss_SIZE = ABSOLUTE(sbss_END - sbss_START);\n");
		bw.write("\t}\n");
		bw.write("\t__romPos += SIZEOF(.sbss);\n");
		bw.write("\t__romPos = ALIGN(__romPos, 8);\n");
		bw.write("\t. = ALIGN(., 8);\n");
		bw.write("\tsbss_ROM_END = __romPos;\n");
		bw.write("\tsbss_VRAM_END = .;\n");
		
		//bss
		bw.write("\n\tbss_ROM_START = __romPos;\n");
		bw.write("\tbss_VRAM = ADDR(.bss);\n");
		bw.write("\t.bss sbss_VRAM_END : AT(bss_ROM_START) SUBALIGN(8)\n");
		bw.write("\t{\n");
		bw.write("\t\tbss_START = .;\n");
		for(BuildModule mod : modules) {
			if(mod.bssPath != null) {
				//bw.write("\t\t" + mod.name + "_bss = .;\n");
				if(bsscomm) {
					bw.write("\t\t" + mod.oPathRel + "(.bss COMMON .scommon);\n");
				}
				else bw.write("\t\t" + mod.oPathRel + "(.bss);\n");
			}
			else {
				if(!mod.useAsm) {
					if(bsscomm) bw.write("\t\t" + mod.oPathRel + "(.bss COMMON .scommon);\n");
					else bw.write("\t\t" + mod.oPathRel + "(.bss);\n");
				}
			}
		}
		bw.write("\t\tbss_END = .;\n");
		bw.write("\t\tbss_SIZE = ABSOLUTE(bss_END - bss_START);\n");
		bw.write("\t}\n");
		bw.write("\t__romPos += SIZEOF(.bss);\n");
		bw.write("\t__romPos = ALIGN(__romPos, 4);\n");
		bw.write("\t. = ALIGN(., 4);\n");
		bw.write("\tbss_ROM_END = __romPos;\n");
		bw.write("\tbss_VRAM_END = .;\n");
		
		bw.write("\n\t/DISCARD/ :\n\t{\n");
		bw.write("\t\t*(*);\n\t}\n");
		
		bw.write("}\n");
		bw.close();
		
	}
	
	private BuildModule parseModuleNode(LiteNode moduleSpec) throws IOException {
		BuildModule mod = new BuildModule();
		
		mod.name = moduleSpec.attr.get("Name");
		if(mod.name == null) {
			Random rand = new Random();
			mod.name = String.format("mod_%08x", rand.nextInt());
		}
		
		mod.libName = moduleSpec.attr.get("LibName");
		String objdir = buildDir + File.separator + "obj";
		if(mod.libName != null) {
			if(mod.libName.startsWith("Lib")) {
				objdir += File.separator + "psx";
			}
			objdir += File.separator + mod.libName;
		}
		if(!FileBuffer.directoryExists(objdir)) {
			Files.createDirectories(Paths.get(objdir));
		}
		mod.oPath = objdir + File.separator;
		
		if(gnuFlag) mod.oPath += mod.name + ".o";
		else {
			mod.oPath = mod.oPath.toUpperCase();
			mod.oPath += mod.name + ".OBJ";
		}
		mod.oPathRel = MyuArcCommon.localPath2UnixRel(indir, mod.oPath);
		
		LiteNode buildSpecNode = moduleSpec.getFirstChildWithName("AsmBuild");
		if(buildSpecNode != null) {
			LiteNode sec = buildSpecNode.getFirstChildWithName("RoData");
			if(sec != null) mod.roDataPath = sec.value;
			sec = buildSpecNode.getFirstChildWithName("Text");
			if(sec != null) mod.textPath = sec.value;
			sec = buildSpecNode.getFirstChildWithName("Data");
			if(sec != null) mod.dataPath = sec.value;
			sec = buildSpecNode.getFirstChildWithName("SData");
			if(sec != null) mod.sdataPath = sec.value;
			sec = buildSpecNode.getFirstChildWithName("Bss");
			if(sec != null) mod.bssPath = sec.value;
			sec = buildSpecNode.getFirstChildWithName("SBss");
			if(sec != null) mod.sbssPath = sec.value;
			mod.useAsm = true;
		}
		else {
			buildSpecNode = moduleSpec.getFirstChildWithName("CCompile");
			if(buildSpecNode != null) {
				mod.useAsm = false;
				LiteNode src = buildSpecNode.getFirstChildWithName("Src");
				if(src == null) {
					MyuPackagerLogger.logMessage("BuildScripts.parseModuleNode", "WARNING: Module \"" + mod.name + "\" included, but no source file(s) specified! Skipping!");
					return null;
				}
				mod.cPath = src.value;
				String aval = buildSpecNode.attr.get("Optimization");
				if(aval != null) mod.cOptLevel = aval;
				else mod.cOptLevel = "O2";
				
				aval = buildSpecNode.attr.get("AsmAdj");
				if(aval != null && aval.equalsIgnoreCase("true")) mod.asmAdj = true;
			}
			else {
				MyuPackagerLogger.logMessage("BuildScripts.parseModuleNode", "WARNING: Module \"" + mod.name + "\" included, but no build method specified! Skipping!");
				return null;
			}
		}
		
		return mod;
	}
	
	private void parseXMLNodes(LiteNode node) throws IOException{
		if(node == null) return;
		if(node.name.equals("ExeBuild")) {
			for(LiteNode child : node.children) {
				parseXMLNodes(child);
			}
		}
		else if(node.name.equals("SysLib")) {
			for(LiteNode child : node.children) {
				parseXMLNodes(child);
			}
		}
		else if(node.name.equals("Module")) {
			BuildModule mod = parseModuleNode(node);
			modules.add(mod);
		}
	}
	
	public static void main_BuildScripts(Map<String, String> argmap) throws IOException {
		BuildScripts ctx = new BuildScripts();
		if(!checkArgs(argmap, ctx)) {
			MyuPackagerLogger.logMessage("BuildScripts.main_BuildScripts", "Argument check failed!");
			System.exit(1);
		}
		
		//Read input XML
		MyuPackagerLogger.logMessage("BuildScripts.main_BuildScripts", "Reading input specs...");
		LiteNode xmlRoot = MyuArcCommon.readXML(ctx.xmlPath);
		ctx.modules = new ArrayList<BuildModule>(64);
		ctx.parseXMLNodes(xmlRoot);
		
		//Build script
		MyuPackagerLogger.logMessage("BuildScripts.main_BuildScripts", "Building asm/compile shell script...");
		BufferedWriter bw = new BufferedWriter(new FileWriter(ctx.shPath));
		bw.write("#!/bin/bash\n\n");
		bw.write(SHVAR_SMERGE + "=./tools/merge_asm_module.sh\n");
		bw.write(SHVAR_INCL_DIR + "=./include\n");
		bw.write(SHVAR_WIN_TOOL + "=./tools/wibo\n");
		bw.write(SHVAR_PSYQ_DIR + "=./tools/psyq4.6\n");
		bw.write(SHVAR_MASPSX + "=./tools/maspsx/maspsx.py\n");
		bw.write(SHVAR_MYUJAR + "=./tools/myu_packager/myupack.jar\n");
		bw.write(SHVAR_MYUPACK + "=\"java -jar ${" + SHVAR_MYUJAR + "}\"\n\n");
		bw.write("wdir=$(pwd)\n");
		bw.write("echo -e \"Working dir: ${wdir}\"\n\n");
		for(BuildModule mod : ctx.modules) ctx.processModuleBuild(mod, bw);
		bw.write("\n");
		bw.close();
		
		//Linker script
		MyuPackagerLogger.logMessage("BuildScripts.main_BuildScripts", "Building linker script...");
		ctx.writeLinkerScript();
		
	}

}
