package waffleoRai_extractMyu;

import java.util.HashMap;
import java.util.Map;

import waffleoRai_extractMyu.mains.ArcBuild;
import waffleoRai_extractMyu.mains.ArcExtract;
import waffleoRai_extractMyu.mains.CheckMatch;
import waffleoRai_extractMyu.mains.IsoExtract;
import waffleoRai_extractMyu.mains.TrackGlue;

public class Main {
	
	public static final String TOOLNAME_UNPACK_ISO = "isounpack";
	public static final String TOOLNAME_UNPACK_ARC = "arcunpack";
	public static final String TOOLNAME_PACK_ARC = "arcpack";
	public static final String TOOLNAME_PACK_ISO = "isopack";
	public static final String TOOLNAME_MATCH_CHECK = "checkmatch";
	public static final String TOOLNAME_GLUE_TRACKS = "gluetracks"; //Glues together a track 1 image and track 2 image into a single CD image.
	
	//For rebuild, have option to omit the CD-DA track 2 data (though the directory entry has to be there for matching)
	
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
		//TODO
		/*
		 * Tools: 
		 * 	- Unpack full ISO/Bin-Cue
		 * 	- Unpack single archive
		 * 	- Convert files to certain target formats (seqp, vh, sprite, probably whatever field, anime, and sce use)
		 * 		- But not vag, audio/video stream, probably also leave compression to external tool?
		 * 		- Sprite input also need options specifying bit depth and such
		 * 	- Package into single archive
		 * 		- Option to not compress, better to leave to external tool
		 * 		- Remember that for some baffling reason, sound files get packaged into little mini-archives too
		 * 	- Package into ISO? (Need to figure out checksums...)
		 * 
		 */
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
		System.err.println("--match\t\tFlag: Try to match original file.");
	}
	
	public static void printUsage_CheckMatch(){
		System.err.println("MyuPackager Check Match ---------- ");
		System.err.println("--ogfile\t\t[Path to original binary archive/stream file to match]");
		System.err.println("--myfile\t\t[Path to generated archive/stream file to check]");
		System.err.println("--outstem\t\t[Pathstem (optional) for dumping mismatching files]");
		System.err.println("--xa\t\tFlag: Reference file is an XA stream");
		System.err.println("--lz\t\tFlag: Reference archive files are compressed");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
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
			}
			if(tool.equalsIgnoreCase(TOOLNAME_UNPACK_ARC)){
				ArcExtract.main_arcUnpack(argmap, "");
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_UNPACK_ISO)){
				IsoExtract.main_isoUnpack(argmap);
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
			
			//Close log
			MyuPackagerLogger.closeLog();
			
			
		}catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
