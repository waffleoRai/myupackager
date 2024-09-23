package waffleoRai_extractMyu.mains;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.Main;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_myuctempl.MCT_Main;

public class AsmSplit {
	
	private static void runMCT(List<String> arglist) {
		int acount = arglist.size();
		String[] arr = new String[acount];
		int i = 0;
		for(String arg : arglist) {
			arr[i++] = arg;
		}
		MCT_Main.main(arr);
	}
	
	private static void scanDataDir(Path dirpath, String textDir, String dataDir, String outDir, String cDir) throws IOException {
		String dirRel = MyuArcCommon.localPath2UnixRel(dataDir, dirpath.toAbsolutePath().toString());
		if(dirRel.startsWith(".")) dirRel = dirRel.substring(1);
		dirRel = dirRel.replace("/", File.separator);
		DirectoryStream<Path> dstr = Files.newDirectoryStream(dirpath);
		for(Path p : dstr) {
			String fn = p.getFileName().toString();
			if(Files.isDirectory(p)) {
				if(fn.equals(".") || fn.equals("..")) continue;
				scanDataDir(p, textDir, dataDir, outDir + File.separator + fn, cDir + File.separator + fn);
			}
			else {
				if(!fn.endsWith(".data.s")) continue;
				String modName = fn.replace(".data.s", "");
				
				//Make sure text file does NOT exist
				String textPath = textDir + dirRel + File.separator + modName + ".s";
				if(FileBuffer.fileExists(textPath)) continue;
				
				String sOutDir = outDir + File.separator + modName;
				if(!FileBuffer.directoryExists(sOutDir)) {
					Files.createDirectories(Paths.get(sOutDir));
				}
				if(!FileBuffer.directoryExists(cDir)) {
					Files.createDirectories(Paths.get(cDir));
				}
				
				String roPath = dataDir + dirRel + File.separator + modName + ".rodata.s";
				
				List<String> args = new LinkedList<String>();
				args.add("stddis");
				args.add("--modname");
				args.add(modName);
				args.add("--data");
				args.add(p.toAbsolutePath().toString());
				
				if(FileBuffer.fileExists(roPath)) {
					args.add("--rodata");
					args.add(roPath);
				}
				
				String scandirs = dataDir + ";" + textDir; 
				args.add("--updirs");
				args.add(scandirs);
				args.add("--downdirs");
				args.add(scandirs);
				
				args.add("--asmoutdir");
				args.add(sOutDir);
				
				args.add("--outc");
				args.add(cDir + File.separator + modName + ".c");
				
				MyuPackagerLogger.logMessage("AsmSplit.scanDataDir", "Analyzing/dumping data module \"" + modName + "\"...");
				runMCT(args);
			}
		}
		dstr.close();
	}
	
	private static void scanTextDir(Path dirpath, String textDir, String dataDir, String outdir, Set<String> modsFound) throws IOException {
		String dirRel = MyuArcCommon.localPath2UnixRel(textDir, dirpath.toAbsolutePath().toString());
		if(dirRel.startsWith(".")) dirRel = dirRel.substring(1);
		dirRel = dirRel.replace("/", File.separator);
		DirectoryStream<Path> dstr = Files.newDirectoryStream(dirpath);
		for(Path p : dstr) {
			String pstr = p.toAbsolutePath().toString();
			String fn = p.getFileName().toString();
			if(Files.isDirectory(p)) {
				if(fn.equals(".") || fn.equals("..")) continue;
				scanTextDir(p, textDir, dataDir, outdir + File.separator + fn, modsFound);
			}
			else {
				if(!fn.endsWith(".s")) continue;
				String modName = fn.replace(".s", "");
				String sOutDir = outdir + File.separator + modName;
				if(!FileBuffer.directoryExists(sOutDir)) {
					Files.createDirectories(Paths.get(sOutDir));
				}

				List<String> args = new LinkedList<String>();
				args.add("stddis");
				args.add("--modname");
				args.add(modName);
				args.add("--text");
				args.add(pstr);
				
				String spath = dataDir + File.separator + dirRel + File.separator + modName + ".rodata.s";
				if(FileBuffer.fileExists(spath)) {
					args.add("--rodata");
					args.add(spath);
				}
				
				spath = dataDir + File.separator + dirRel + File.separator + modName + ".data.s";
				if(FileBuffer.fileExists(spath)) {
					args.add("--data");
					args.add(spath);
				}
				
				String scandirs = dataDir + ";" + textDir; 
				args.add("--updirs");
				args.add(scandirs);
				args.add("--downdirs");
				args.add(scandirs);
				
				args.add("--asmoutdir");
				args.add(sOutDir);
				
				MyuPackagerLogger.logMessage("AsmSplit.scanTextDir", "Analyzing/splitting module \"" + modName + "\"...");
				runMCT(args);
			}
		}
		dstr.close();
	}
	
	public static void main_asmSplit(Map<String, String> argmap) throws IOException {
		//Scans through asm dir, finds unique modules, and just calls MCT_Main on each
		String asmdir = MyuArcCommon.getSystemAbsolutePath(argmap.get("asmdir"));
		String outdir = MyuArcCommon.getSystemAbsolutePath(argmap.get("outdir"));
		String cdir = MyuArcCommon.getSystemAbsolutePath(argmap.get("cdir"));
		
		if(asmdir == null) {
			MyuPackagerLogger.logMessage("AsmSplit.main_asmSplit", "ASM directory path is required!");
			Main.printUsage_AsmSplit();
			System.exit(1);
		}
		
		if(!FileBuffer.directoryExists(asmdir)) {
			MyuPackagerLogger.logMessage("AsmSplit.main_asmSplit", "Input directory \"" + asmdir + "\" does not exist!");
			Main.printUsage_AsmSplit();
			System.exit(1);
		}
		
		if(outdir == null || outdir.isEmpty()) {
			outdir = asmdir + File.separator + "fsplit";
			MyuPackagerLogger.logMessage("AsmSplit.main_asmSplit", "Output directory not provided. Set to \"" + outdir + "\"");
		}
		
		//Do text
		Set<String> modsFound = new HashSet<String>();
		String textDir = asmdir + File.separator + "text";
		String dataDir = asmdir + File.separator + "data";
		scanTextDir(Paths.get(textDir), textDir, dataDir, outdir, modsFound);
		
		//Do data only sections (extract to .c)
		if(cdir != null) {
			scanDataDir(Paths.get(dataDir), textDir, dataDir, outdir, cdir);
		}
	}

}
