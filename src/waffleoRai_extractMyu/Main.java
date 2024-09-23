package waffleoRai_extractMyu;

import java.util.HashMap;
import java.util.Map;

import waffleoRai_extractMyu.mains.ArcBuild;
import waffleoRai_extractMyu.mains.ArcExtract;
import waffleoRai_extractMyu.mains.AsmSplit;
import waffleoRai_extractMyu.mains.BuildScripts;
import waffleoRai_extractMyu.mains.CDCompare;
import waffleoRai_extractMyu.mains.CheckMatch;
import waffleoRai_extractMyu.mains.CtxGen;
import waffleoRai_extractMyu.mains.Data2C;
import waffleoRai_extractMyu.mains.DumpBss;
import waffleoRai_extractMyu.mains.Elf2PsxExe;
import waffleoRai_extractMyu.mains.GenSplat;
import waffleoRai_extractMyu.mains.IsoBuild;
import waffleoRai_extractMyu.mains.IsoExtract;
import waffleoRai_extractMyu.mains.ObjCheck;
import waffleoRai_extractMyu.mains.PCMMe;
import waffleoRai_extractMyu.mains.PsyqObjDumper;
import waffleoRai_extractMyu.mains.Rmjrra;
import waffleoRai_extractMyu.mains.SpuEnc;
import waffleoRai_extractMyu.mains.SymbolHist;
import waffleoRai_extractMyu.mains.TrackGlue;

public class Main {
	
	//TODO Add options for isounpack and isopack to NOT build/unpackage archives automatically
	//For rebuild, have option to omit the CD-DA track 2 data (though the directory entry has to be there for matching)
	
	//TODO Not yet implemented:
	
	public static final String TOOLNAME_UNPACK_ISO = "isounpack";
	public static final String TOOLNAME_UNPACK_ARC = "arcunpack";
	public static final String TOOLNAME_PACK_ARC = "arcpack";
	public static final String TOOLNAME_PACK_ISO = "isopack";
	public static final String TOOLNAME_MATCH_CHECK = "checkmatch";
	public static final String TOOLNAME_GLUE_TRACKS = "gluetracks"; //Glues together a track 1 image and track 2 image into a single CD image.
	public static final String TOOLNAME_DUMP_PSYQ_OBJ = "obj2xml";
	public static final String TOOLNAME_BSS_2_ASM = "bss2asm";
	public static final String TOOLNAME_SPLITASM = "asmsplit";
	public static final String TOOLNAME_GENBLDSCR = "genbld";
	public static final String TOOLNAME_GEN_SPL_YAML = "splatyaml";
	public static final String TOOLNAME_CHECKOBJ = "chkobj";
	public static final String TOOLNAME_PSXEXE_2_ELF = "psxexe2elf";
	public static final String TOOLNAME_ELF_2_PSXEXE = "elf2psxexe";
	public static final String TOOLNAME_PCM_ME = "pcmme"; //For VAG/ADPCM decoding
	public static final String TOOLNAME_SPUENC = "spuenc"; //For VAG/ADPCM encoding
	
	public static final String TOOLNAME_DATA2C = "data2c";
	public static final String TOOLNAME_CTXGEN = "ctxgen";
	public static final String TOOLNAME_SYMHIST_UPDATE = "symhistupd";
	public static final String TOOLNAME_SYMHIST_REPLACE = "symhistrpl";
	public static final String TOOLNAME_CD_COMPARE = "cdcmp";
	
	public static final String TOOLNAME_RM_DUMMY_RET = "rmjrra";
	
	public static final String TOOLNAME_TESTNATIVE = "testnative";
	
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
	
	public static void printUsage(){
		System.err.println("--------- MyuPackager Tools ---------");
		
		System.err.println("\nUnpacking/Disassembly:");
		System.err.println("\t[isounpack] - Unpack files from CD image");
		System.err.println("\t[arcunpack] - Unpack assets from binary archive or XA stream");
		System.err.println("\t[bss2asm] - Generate asm scripts for .bss sections");
		System.err.println("\t[asmsplit] - Split assembly files into individual files for each function/symbol");
		System.err.println("\t[splatyaml] - Generate a YAML file for splat input from section tsv table");
		System.err.println("\t[data2c] - Dump data-only sections to .c files");
		System.err.println("\t[pcmme] - Convert a PSX ADPCM audio clip to PCM (wav or aiff)");
		
		System.err.println("\nPacking/Build:");
		System.err.println("\t[isopack] - Package files into CD image");
		System.err.println("\t[arcpack] - Package assets into binary archive or XA stream");
		System.err.println("\t[genbld] - Generates script chain to build code binary file");
		System.err.println("\t[elf2psxexe] - Repackage contents from an ELF file into a PSXEXE");
		System.err.println("\t[gluetracks] - If tracks 1 and 2 were dumped as separate files, this can glue them into one bin");
		System.err.println("\t[spuenc] - Encode a PCM audio file as PSX ADPCM");
		
		System.err.println("\nDecomp Assistance:");
		System.err.println("\t[ctxgen] - Generate context file for decomp.me");
		System.err.println("\t[symhistupd] - Update symbol history file from most recent symbol lists");
		System.err.println("\t[symhistrpl] - Update symbol names in .c and .h files");
		
		System.err.println("\nTesting/Debug:");
		System.err.println("\t[checkmatch] - Scan individual asset files in archive or XA stream to see if they match original");
		System.err.println("\t[chkobj] - Compare an ELF object to original binary to find mismatches");
		System.err.println("\t[obj2xml] - Dump the command flow of a PsyQ OBJ file to xml (Does not dump section contents)");
		System.err.println("\t[testnative] - Check whether packager can find and load native component (liblzmu)");
	}
	
	public static void printUsage_IsoUnpack(){
		System.err.println("MyuPackager ISO Unpack ---------- ");
		System.err.println("--iso\t\t[Path to input CD image]");
		System.err.println("--cue\t\t[Path to cue table if input image is a bin]");
		System.err.println("--cdout\t\t[Path to directory to place extracted CD contents]");
		System.err.println("--assetout\t\t[Path to directory to place extracted assets]");
		System.err.println("--arcspec\t\t[Path to directory containing tables w/ file info for archives (file info, VOICE ptr table etc.)]");
		System.err.println("--checksums\t\t[Path to csv containing checksums for vanilla game files]");
		System.err.println("--xmlout\t\t[Path to xml file output]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_SplatYaml(){
		System.err.println("MyuPackager Gen Splat YAML ---------- ");
		System.err.println("--exepath\t\t[Path to input PSXEXE file]");
		System.err.println("--out\t\t[Path to output yaml]");
		System.err.println("--sectbl\t\t[Path to section table file]");
		System.err.println("--ops\t\t[Path to splat options YAML to use as base]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_Data2C(){
		System.err.println("MyuPackager Data 2 C ---------- ");
		System.err.println("--xml\t\t[(Multi) Path to input xml spec]");
		System.err.println("--input\t\t[(Single) Path to input asm (.s) file]");
		System.err.println("--output\t\t[(Single) Path to output .c file]");
		System.err.println("--hname\t\t[(Single) Name of header file to #include. Defaults to name of input file.]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_AsmSplit(){
		System.err.println("MyuPackager ASM Split ---------- ");
		System.err.println("--asmdir\t\t[Path to asm base directory]");
		System.err.println("--outdir\t\t[Path of output directory]");
		System.err.println("--cdir\t\t[Path to put .c data files - if arg is not provided these are not dumped]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_Isopack(){
		System.err.println("MyuPackager ISO Pack ---------- ");
		System.err.println("--spec\t\t[Path to XML file containing build specifications]");
		System.err.println("--out\t\t[Path of output ISO file]");
		System.err.println("--checksums\t\t[Path of csv table containing file/image checksums]");
		System.err.println("--builddir\t\t[Path of directory to use for build staging]");
		System.err.println("-match\t\tFlag: Build in match mode to try to match original image");
		System.err.println("-buildall\t\tFlag: Rebuild all archives/streams before incorporating");
		System.err.println("-arconly\t\tFlag: Only build archives/streams and do not package into CD image");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_DumpPsyqObj(){
		System.err.println("MyuPackager PsyQ OBJ Dump ---------- ");
		System.err.println("--input\t\t[Path to OBJ or LIB file]");
		System.err.println("--xmlout\t\t[Path to xml file output. Should be directory for LIB or file for OBJ.]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_DumpBss(){
		System.err.println("MyuPackager .bss to asm Dump ---------- ");
		System.err.println("--sectbl\t\t[Table of sections (tab-delimited)]");
		System.err.println("--dsymbols\t\t[File listing data symbols (as used for splat)]");
		System.err.println("--asmdir\t\t[Path to asm base directory for placing output files]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_ArcUnpack(){
		//Note that it can still run without any specs, but it'll just dump binaries.
		System.err.println("MyuPackager Archive Unpack ---------- ");
		System.err.println("--input\t\t[Path to binary archive/stream file]");
		System.err.println("--output\t\t[Path to output directory to place archive contents]");
		System.err.println("--arcspec\t\t[Path to xml file containing info about arc/stream contents]");
		System.err.println("--xmlout\t\t[Path to xml file output]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_ArcPack(){
		System.err.println("MyuPackager Archive Pack ---------- ");
		System.err.println("--arcspec\t\t[Path to xml file containing specification for building arc/stream]");
		System.err.println("--output\t\t[Output path for archive bin]");
		System.err.println("--hout\t\t[Path to output .h file containing file enum definition]");
		System.err.println("--cout\t\t[Path to output .c file containing offset table data]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
		System.err.println("-match\t\tFlag: Try to match original file.");
	}
	
	public static void printUsage_CheckMatch(){
		System.err.println("MyuPackager Check Match ---------- ");
		System.err.println("--ogfile\t\t[Path to original binary archive/stream file to match]");
		System.err.println("--myfile\t\t[Path to generated archive/stream file to check]");
		System.err.println("--outstem\t\t[Pathstem (optional) for dumping mismatching files]");
		System.err.println("-xa\t\tFlag: Reference file is an XA stream");
		System.err.println("-lz\t\tFlag: Reference archive files are compressed");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_BuildScripts(){
		System.err.println("MyuPackager Build Script Generator  ---------- ");
		System.err.println("--xmlspec\t\t[Path to XML file containing build spec]");
		System.err.println("--builddir\t\t[Directory to put temp build files]");
		System.err.println("--shout\t\t[Path to generated shell script (Defaults to: ${INPUT_DIR}/myubuild.sh]");
		System.err.println("--ldout\t\t[Path to generated link script (Defaults to: ${INPUT_DIR}/myubuild.ld]");
		System.err.println("--binout\t\t[Path to output file. This IS NOT the final psxexe!]");
		System.err.println("-gnu\t\tFlag: Use the mips-linux-gnu build system instead of psyq");
		System.err.println("-wsl\t\tFlag: Running in WSL. Don't need wine/wibo for psyq.");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_CheckObjMatch(){
		//TODO
		System.err.println("MyuPackager Object Match Checker  ---------- ");
		System.err.println("--sectbl\t\t[Path to tab-delimited section table]");
		System.err.println("--builddir\t\t[Path to directory where obj files can be found]");
		System.err.println("--exefile\t\t[Path to original psxexe to check against]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_psxexe2elf(){
		//TODO
		System.err.println("MyuPackager PSXEXE 2 ELF  ---------- ");
		System.err.println("--sectbl\t\t[Path to tab-delimited section table]");
		//TODO Eh maybe I'll do that later. Would need to find relocs. Euh
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void printUsage_elf2psxexe(){
		System.err.println("MyuPackager ELF 2 PSXEXE  ---------- ");
		System.err.println("--input\t\t[Path to input ELF]");
		System.err.println("--output\t\t[Path to output PSXEXE]");
		System.err.println("-jp\t\t[Set region to Japan (Default)]");
		System.err.println("-na\t\t[Set region to North America]");
		System.err.println("-eu\t\t[Set region to Europe]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	private static void testNative() {
		boolean okay = LzNative.libLoaded();
		if(okay) {
			System.err.println("SUCCESS: LZMu native link test returned true!");
		}
		else {
			System.err.println("FAILURE: LZMu library could not be loaded.");
		}
	}
	
	public static void main(String[] args) {
		if(args == null || args.length < 1){
			printUsage();
			System.exit(1);
		}
		
		try{
			Map<String, String> argmap = parseargs(args);
			
			//Set up callbacks
			MyuArcCommon.loadStandardTypeHandlers();
			
			//Set up log
			String logpath = argmap.get("log");
			if(logpath != null){
				MyuPackagerLogger.openLog(logpath);
			}
			else{
				MyuPackagerLogger.openLogStdErr();
			}
			
			//Determine tool and call that tool's main
			String tool = args[0];
			if(tool.isEmpty()){
				MyuPackagerLogger.logMessage("Main.main", "No tool specified! Exiting...");
				printUsage();
			}
			if(tool.equalsIgnoreCase(TOOLNAME_UNPACK_ARC)){
				ArcExtract.main_arcUnpack(argmap, "");
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_UNPACK_ISO)){
				IsoExtract.main_isoUnpack(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_PACK_ISO)){
				IsoBuild.main_isoPack(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_PACK_ARC)){
				ArcBuild.main_arcPack(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_GLUE_TRACKS)){
				TrackGlue.main_glueTracks(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_MATCH_CHECK)){
				CheckMatch.main_matchCheck(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_DUMP_PSYQ_OBJ)){
				PsyqObjDumper.main_obj2xml(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_BSS_2_ASM)){
				DumpBss.main_dumpbss(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_GEN_SPL_YAML)){
				GenSplat.main_genSplat(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_SPLITASM)){
				AsmSplit.main_asmSplit(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_GENBLDSCR)){
				BuildScripts.main_BuildScripts(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_CHECKOBJ)){
				ObjCheck.main_objcheck(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_ELF_2_PSXEXE)){
				Elf2PsxExe.main_elf2psxexe(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_DATA2C)){
				Data2C.main_data2c(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_CD_COMPARE)){
				CDCompare.main_cdCompare(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_CTXGEN)){
				CtxGen.main_ctxgen(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_SYMHIST_UPDATE)){
				SymbolHist.main_updateSymHistRecord(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_SYMHIST_REPLACE)){
				SymbolHist.main_updateCodeSymbols(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_RM_DUMMY_RET)){
				Rmjrra.main_rmjrra(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_PCM_ME)){
				PCMMe.main_pcmme(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_SPUENC)){
				SpuEnc.main_spuenc(argmap);
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_TESTNATIVE)){
				testNative();
			}
			
			//Close log
			MyuPackagerLogger.closeLog();
			
			
		}catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
