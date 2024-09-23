package waffleoRai_myuctempl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_myuctempl.AsmScanning.AsmLine;

public class MCT_Main {
	
	//TODO Untether .h and .c outputs to tool so can run as splitter only
	
	public static final String MODE_TEMPLGEN_STR = "gentmpl";
	public static final String MODE_STD_STR = "stddis";
	
	public static final int MODE_NONE = -1;
	public static final int MODE_TEMPLGEN = 1;
	public static final int MODE_STD = 2;
	
	private static int parseLiteral(String str) {
		if(str.startsWith("0x")) return Integer.parseUnsignedInt(str.substring(2), 16);
		return Integer.parseInt(str);
	}
	
	private static Map<String, String> parseargs(String[] args){
		Map<String, String> map = new HashMap<String, String>();
		if(args != null){
			int acount = args.length;
			for(int i = 1; i < acount; i++){
				if (args[i].startsWith("--")){
					//Key value pair
					String arg = args[i].replace("--", "");
					map.put(arg, args[++i]);
				}
				else{
					//Flag
					String arg = args[i].replace("-", "");
					map.put(arg, "true");
				}
			}
		}
		return map;
	}
	
	private static boolean checkArgs(Map<String, String> args, MCTModule target, int mode) {
		target.textPath = args.get("text");
		target.rodataPath = args.get("rodata");
		target.dataPath = args.get("data");
		target.cPath = args.get("outc");
		target.hPath = args.get("outh");
		target.sSplitDir = args.get("asmoutdir");
		target.modName = args.get("modname");
		
		String val = args.get("bsssize");
		if(val != null) {
			try {
				if(val.startsWith("0x")) {
					target.bssSize = Integer.parseUnsignedInt(val.substring(2), 16);
				}
				else {
					target.bssSize = Integer.parseInt(val);
				}
			}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				return false;
			}	
		}
		else {
			System.err.println("Warning: .bss size not specified. Assumed absent.");
		}
		
		val = args.get("updirs");
		if(val != null) {
			String[] spl = val.split(";");
			for(String s : spl) target.upDirs.add(s);
		}
		
		val = args.get("downdirs");
		if(val != null) {
			String[] spl = val.split(";");
			for(String s : spl) target.downDirs.add(s);
		}
		
		//Determine mandatory paths
		if((target.textPath == null) && (target.dataPath == null) && (target.rodataPath == null)) {
			System.err.println("At least one input file is required!");
			return false;
		}
		
		String defoDir = null;
		String[] checkPaths = {target.sSplitDir, target.cPath, target.hPath, target.textPath, target.dataPath, target.rodataPath};
		for(int i = 0; i < checkPaths.length; i++) {
			if(checkPaths[i] == null) continue;
			defoDir = checkPaths[i].substring(0, checkPaths[i].lastIndexOf(File.separator));
			break;
		}
		if(target.modName == null) {
			//Pull from one of the sources
			for(int i = 0; i < checkPaths.length; i++) {
				if(checkPaths[i] == null) continue;
				target.modName = checkPaths[i].substring(checkPaths[i].lastIndexOf(File.separator) + 1);
				while(target.modName.contains(".")) {
					target.modName = target.modName.substring(0, target.modName.lastIndexOf('.'));
				}
				break;
			}
			System.err.println("Module name not specified. Set to \"" + target.modName + "\"");
		}
		
		if(defoDir == null) {
			System.err.println("Default directory could not be determined!");
			return false;
		}
		
		//Now check all paths.
		if(target.textPath != null) {
			if(!FileBuffer.fileExists(target.textPath)) {
				System.err.println("Input .text file \"" + target.textPath + "\" does not exist! Exiting...");
				return false;
			}
		}
		if(target.dataPath != null) {
			if(!FileBuffer.fileExists(target.dataPath)) {
				System.err.println("Input .data file \"" + target.textPath + "\" does not exist! Exiting...");
				return false;
			}
		}
		if(target.rodataPath != null) {
			if(!FileBuffer.fileExists(target.rodataPath)) {
				System.err.println("Input .rodata file \"" + target.textPath + "\" does not exist! Exiting...");
				return false;
			}
		}
		
		
		if((mode == MODE_TEMPLGEN) && (target.cPath == null)) {
			target.cPath = defoDir + File.separator + target.modName + ".c";
			System.err.println("C output not specified. Set to \"" + target.cPath + "\"");
		}
		if((mode == MODE_TEMPLGEN) && (target.hPath == null)) {
			target.hPath = defoDir + File.separator + target.modName + ".h";
			System.err.println("H output not specified. Set to \"" + target.hPath + "\"");
		}
		if(target.sSplitDir == null) {
			target.sSplitDir = defoDir + File.separator + target.modName;
			System.err.println("ASM split dir output not specified. Set to \"" + target.sSplitDir + "\"");
		}
		
		try {
			if(!FileBuffer.directoryExists(target.sSplitDir)) {
				Files.createDirectories(Paths.get(target.sSplitDir));
			}
		}
		catch(IOException ex) {
			ex.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private static void scanDownstreamDir(Path dirPath, MCTModule module, boolean recursive) throws IOException {
		DirectoryStream<Path> dstr = Files.newDirectoryStream(dirPath);
		for(Path p : dstr) {
			if(Files.isDirectory(p)) {
				if(!recursive) continue;
				else scanDownstreamDir(p, module, recursive);
			}
			else {
				String pstring = p.toAbsolutePath().toString();
				
				//Skip if one of the inputs
				if(pstring.equals(module.textPath)) continue;
				if(pstring.equals(module.dataPath)) continue;
				if(pstring.equals(module.rodataPath)) continue;
				
				if(pstring.endsWith(".s")) {
					AsmScanning.scanForSymbols(pstring, module);
				}	
			}
		}
		dstr.close();
	}
	
	private static void scanUpstreamDir(Path dirPath, MCTModule module, Set<String> foundDeps, boolean recursive) throws IOException {
		DirectoryStream<Path> dstr = Files.newDirectoryStream(dirPath);
		for(Path p : dstr) {
			if(Files.isDirectory(p)) {
				if(!recursive) continue;
				else scanUpstreamDir(p, module, foundDeps, recursive);
			}
			String pstring = p.toAbsolutePath().toString();
			//Skip if one of the inputs
			if(pstring.equals(module.textPath)) continue;
			if(pstring.equals(module.dataPath)) continue;
			if(pstring.equals(module.rodataPath)) continue;
			
			if(pstring.endsWith(".s")) {
				Set<String> othersyms = AsmScanning.quickGetSymbols(pstring);
				if(othersyms != null && !othersyms.isEmpty()) {
					//Get file name
					String fn = p.getFileName().toString();
					while(fn.contains(".")) {
						fn = fn.substring(0, fn.lastIndexOf('.'));
					}
					
					//Look for any match
					for(String othersym : othersyms) {
						if(module.allDeps.contains(othersym)) {
							foundDeps.add(fn);
							break;
						}
					}
				}
			}
		}
		dstr.close();
	}

	private static void genHFile(MCTModule module, Set<String> foundDeps, String outpath) throws IOException {
		String ifdefName = "MYUC_" + module.modName.toUpperCase() + "_H";
		BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
		bw.write("#ifndef " + ifdefName + "\n");
		bw.write("#define " + ifdefName + "\n\n");
		
		bw.write("/*---------------------------------------\n");
		bw.write("* Header file autogenerated by MyuCTemplatizer\n");
		bw.write("* ---------------------------------------*/\n\n");
		
		if(!foundDeps.isEmpty()) {
			List<String> alphaList = new ArrayList<String>(foundDeps.size());
			alphaList.addAll(foundDeps);
			Collections.sort(alphaList);
			for(String dep : alphaList) {
				bw.write("#include \"" + dep + "\"\n");
			}
		}
		bw.write("\n#ifdef __cplusplus\n");
		bw.write("extern \"C\" {\n");
		bw.write("#endif\n\n");
				
		//Public rodata
		int count = 0;
		for(MCTSymbol symbol : module.rodataSymbols) {
			if(symbol.isPublic) {
				count++;
				bw.write("\textern const " + symbol.guessReturnType() + " " + symbol.name + "[];\n");
			}
		}
		if(count > 0) bw.write("\n");
		
		count = 0;
		for(MCTSymbol symbol : module.textSymbols) {
			if(symbol.isPublic) {
				count++;
				bw.write("\t" + symbol.guessReturnType() + " " + symbol.name + "(void);\n");
			}
		}
		if(count > 0) bw.write("\n");
		
		count = 0;
		for(MCTSymbol symbol : module.dataSymbols) {
			if(symbol.isPublic) {
				count++;
				bw.write("\textern " + symbol.guessReturnType() + " " + symbol.name + "[];\n");
			}
		}
		if(count > 0) bw.write("\n");
		
		if(module.bssSize > 0) {
			bw.write("\tstatic uint8_t " + module.modName + "_bss[];\n\n");
		}
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("}\n");
		bw.write("#endif\n\n");
		
		bw.write("#endif\n");
		bw.close();
	}
	
	private static void writeCDataTable(MCTSymbol symbol, BufferedWriter bw) throws IOException {
		String retEst = symbol.guessReturnType();
		boolean isConst = (symbol.section == MCTSymbol.SYMBOL_TYPE_RODATA);
		if(symbol.address != 0x0L) {
			if(symbol.section == MCTSymbol.SYMBOL_TYPE_RODATA) {
				bw.write(String.format("//   .rodata 0x%08x\n", symbol.address));
			}
			else if(symbol.section == MCTSymbol.SYMBOL_TYPE_DATA) {
				bw.write(String.format("//   .data 0x%08x\n", symbol.address));
			}
		}
		bw.write("static ");
		if(isConst) bw.write("const ");
		bw.write(retEst + " " + symbol.name + "[] = {\n\t");
		
		int i = 0;
		int perLine = 6;
		boolean isPtrTable = retEst.endsWith("*");
		for(String line : symbol.asmLines) {
			AsmLine aline = AsmScanning.parseLine(line);
			if(aline == null) continue;
			if(aline.cmd.equals(".size")) break;
			
			if(aline.cmd.equals(".word")) {
				if(isPtrTable) {
					bw.write(aline.args.get(0));
				}
				else {
					bw.write(String.format("0x%08x", parseLiteral(aline.args.get(0))));
				}
			}
			else if(aline.cmd.equals(".short")) {
				perLine = 8;
				bw.write(String.format("0x%04x", parseLiteral(aline.args.get(0))));
			}
			else if(aline.cmd.equals(".byte")) {
				perLine = 16;
				bw.write(String.format("0x%02x", parseLiteral(aline.args.get(0))));
			}
			else continue;
			
			bw.write(", ");
			if((++i % perLine) == 0) bw.write("\n\t");
		}
		
		bw.write("};\n");
	}
	
	private static void genCFile(MCTModule module, String outpath) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
		
		bw.write("/*---------------------------------------\n");
		bw.write("* .c file template autogenerated by MyuCTemplatizer\n");
		bw.write("* ---------------------------------------*/\n\n");
		
		bw.write("#include \"" + module.modName + ".h\"\n\n");
		
		bw.write("\n#ifdef __cplusplus\n");
		bw.write("extern \"C\" {\n");
		bw.write("#endif\n\n");
		
		//Function prototypes not in h
		for(MCTSymbol symbol : module.textSymbols) {
			if(!symbol.isPublic) {
				bw.write(symbol.guessReturnType() + " " + symbol.name + "(void);\n");
			}
		}
		bw.write("\n");
		
		for(MCTSymbol symbol : module.rodataSymbols) {
			if(symbol.isPublic) {
				writeCDataTable(symbol, bw);
				bw.write("\n");
			}
		}
		
		for(MCTSymbol symbol : module.dataSymbols) {
			writeCDataTable(symbol, bw);
			bw.write("\n");
		}
		
		if(module.bssSize > 0) {
			bw.write("static uint8_t " + module.modName + "_bss[0x" + Integer.toHexString(module.bssSize) + "];\n\n");
		}
		
		for(MCTSymbol symbol : module.textSymbols) {
			if(symbol.address != 0L) {
				bw.write(String.format("//   .text 0x%08x\n", symbol.address));
			}
			
			bw.write("#pragma GLOBAL_ASM(\"");
			bw.write(module.modName + "/" + symbol.name + ".s");
			bw.write("\")\n\n");
		}
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("}\n");
		bw.write("#endif\n\n");
		
		bw.close();
	}
	
	private static void writeAsmFiles(MCTModule module) throws IOException {
		//Functions
		Set<String> rodataAssigned = new HashSet<String>();
		for(MCTSymbol symbol : module.textSymbols) {
			String spath = module.sSplitDir + File.separator + symbol.name + ".s";
			BufferedWriter bw = new BufferedWriter(new FileWriter(spath));
			//bw.write(".include \"macro.inc\"\n\n");
			bw.write(".set noat\n");
			bw.write(".set noreorder\n\n");
			bw.write("/* Split from spimdisasm 1.27.0 output */\n\n");
			
			if(!symbol.exclusiveRodata.isEmpty()) {
				bw.write(".section .rodata\n\n");
				for(MCTSymbol roSym : symbol.exclusiveRodata) {
					rodataAssigned.add(roSym.name);
					for(String line : roSym.asmLines) bw.write(line + "\n");
					bw.write("\n");
				}
				bw.write("\n");
			}
			
			bw.write(".section .text, \"ax\"\n\n");
			for(String line : symbol.asmLines) bw.write(line + "\n");
			bw.write("\n");
			
			bw.close();
		}
		
		//rodata common
		int usedRoCount = rodataAssigned.size();
		int totalRoCount = module.rodataSymbols.size();
		if((totalRoCount - usedRoCount) > 0) {
			String spath = module.sSplitDir + File.separator + module.modName + ".rodata.s";
			BufferedWriter bw = new BufferedWriter(new FileWriter(spath));
			//bw.write(".include \"macro.inc\"\n\n");
			bw.write(".section .rodata\n\n");
			bw.write("/* Split from spimdisasm 1.27.0 output */\n\n");
			for(MCTSymbol roSym : module.rodataSymbols) {
				if(rodataAssigned.contains(roSym.name)) continue;
				for(String line : roSym.asmLines) bw.write(line + "\n");
				bw.write("\n");
			}
			bw.write("\n");
			bw.close();
		}
		
		//data common
		if(!module.dataSymbols.isEmpty()) {
			String spath = module.sSplitDir + File.separator + module.modName + ".data.s";
			BufferedWriter bw = new BufferedWriter(new FileWriter(spath));
			//bw.write(".include \"macro.inc\"\n\n");
			bw.write(".section .data\n\n");
			bw.write("/* Split from spimdisasm 1.27.0 output */\n\n");
			for(MCTSymbol sym : module.dataSymbols) {
				for(String line : sym.asmLines) bw.write(line + "\n");
				bw.write("\n");
			}
			bw.write("\n");
			bw.close();
		}

	}
	
	public static void printUsage(){
		System.err.println("Myu C Template Generator ---------- ");
		System.err.println("--text\t\t[Path to .s file for .text section of module to process]");
		System.err.println("--rodata\t\t[Path to .s file for .rodata section of module to process]");
		System.err.println("--data\t\t[Path to .s file for .data section of module to process]");
		System.err.println("--bsssize\t\t[Size (in bytes) of module's .bss section]");
		System.err.println("--modname\t\t[Desired name of module]");
		System.err.println("--outc\t\t[Path to output .c file]");
		System.err.println("--outh\t\t[Path to output .h file]");
		System.err.println("--updirs\t\t[Semicolon delimited list of directories to check for dependencies]");
		System.err.println("--downdirs\t\t[Semicolon delimited list of directories to check for dependents]");
		System.err.println("--asmoutdir\t\t[Output directory to write .s scripts for individual functions]");
	}

	public static void main(String[] args) {
		if(args.length < 1) {
			printUsage();
			System.exit(1);
		}
		
		String mode = args[0];
		int mode_i = MODE_NONE;
		if(mode.equals(MODE_TEMPLGEN_STR)) {
			System.err.println("Mode: Template generation");
			mode_i = MODE_TEMPLGEN;
		}
		else if(mode.equals(MODE_STD_STR)) {
			System.err.println("Mode: Standard split");
			mode_i = MODE_STD;
		}
		else {
			System.err.println("Mode \"" + mode + "\" not recognized!");
			printUsage();
			System.exit(1);
		}
		
		Map<String, String> argmap = parseargs(args);
		
		MCTModule module = new MCTModule();
		if(!checkArgs(argmap, module, mode_i)) {
			printUsage();
			System.exit(1);
		}
		
		try {
			//Read data
			if(module.dataPath != null) {
				AsmScanning.readInSymbols(module.dataPath, module);
			}
			
			//Read rodata
			if(module.rodataPath != null) {
				AsmScanning.readInSymbols(module.rodataPath, module);
			}
			
			//Read text
			if(module.textPath != null) {
				AsmScanning.readInSymbols(module.textPath, module);
			}
			
			module.linkLocalROData();
			
			if(mode_i == MODE_TEMPLGEN) {
				//Scan dependents
				for(String ddir : module.downDirs) {
					if(!FileBuffer.directoryExists(ddir)) {
						System.err.println("Directory \"" + ddir + "\" does not exist. Skipping...");
						continue;
					}
					scanDownstreamDir(Paths.get(ddir), module, true);
				}
				
				//Update rodata links
				module.linkLocalROData();
				
				//Scan dependencies
				Set<String> depModules = new HashSet<String>();
				for(String ddir : module.upDirs) {
					if(!FileBuffer.directoryExists(ddir)) {
						System.err.println("Directory \"" + ddir + "\" does not exist. Skipping...");
						continue;
					}
					scanUpstreamDir(Paths.get(ddir), module, depModules, true);
				}
				
				//Output .s, .c and .h files
				genHFile(module, depModules, module.hPath);
				genCFile(module, module.cPath);
				writeAsmFiles(module);
			}
			else if(mode_i == MODE_STD) {
				//If data only, dump c file
				if(module.textSymbols.isEmpty() && (module.cPath != null)) {
					genCFile(module, module.cPath);
				}
				writeAsmFiles(module);
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
