package waffleoRai_extractMyu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import waffleoRai_Containers.ISOXAImage;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_Utils.StringUtils;

public class Main {
	
	public static final String TOOLNAME_UNPACK_ISO = "isounpack";
	public static final String TOOLNAME_UNPACK_ARC = "arcunpack";
	public static final String TOOLNAME_PACK_ARC = "arcpack";
	public static final String TOOLNAME_PACK_ISO = "isopack";
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
	
	private static void main_isoUnpack(Map<String, String> args) throws IOException{
		//TODO
		String input_path = args.get("iso");
		String output_dir_cd = args.get("cdout");
		String output_dir_asset = args.get("assetout");
		String spec_path = args.get("arcspec"); //Arcspec dir
		String xml_path = args.get("xmlout");
		String checksum_path = args.get("checksums");
		
		//--- Check paths
		boolean is_cue = false;
		if(input_path == null) {
			//Check for cue sheet instead
			input_path = args.get("cue");
			is_cue = true;
			if(input_path == null){
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Input file argument is required!");
				return;
			}
			if(!FileBuffer.fileExists(input_path)){
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Input file \"" + input_path + "\" does not exist!");
				return;
			}
		}
		
		String input_dir = input_path.substring(0, input_path.lastIndexOf(File.separatorChar));
		
		if(output_dir_cd == null) {
			output_dir_cd = input_dir + File.separator + "cd";
			MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
					"CD files directory was not provided. Creating directory in input folder (" + output_dir_cd + ")");
		}
		if(output_dir_asset == null) {
			output_dir_asset = input_dir + File.separator + "assets";
			MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
					"Assets directory was not provided. Creating directory in input folder (" + output_dir_asset + ")");
		}
		
		if(!FileBuffer.directoryExists(output_dir_cd)) {
			Files.createDirectories(Paths.get(output_dir_cd));
		}
		if(!FileBuffer.directoryExists(output_dir_asset)) {
			Files.createDirectories(Paths.get(output_dir_asset));
		}
		
		if((checksum_path == null) || (!FileBuffer.fileExists(checksum_path))) {
			checksum_path = input_path + File.separator + "checksums.csv";
			MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
					"Checksum table path was not provided, or table does not exist. Setting to " + checksum_path);
		}
		
		//Read CUE file to find actual binary, if applicable
		if(is_cue) {
			MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
					"Attempting to find image binary from CUE file \"" + input_path + "\"");
			input_path = MyuArcCommon.findBIN_fromCUE(input_path);
			if(input_path == null) {
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Image BIN could not be found! Returning...");
				return;
			}
		}
		
		//--- Input image checksum (if checksums provided)
		//Here, this is mostly for warning the user if provided image is heretofore unknown and results may not be as they expect.
		String[][] checksums = MyuArcCommon.loadChecksumTable(checksum_path); //Returns null if file doesn't exist.
		if(checksums != null) {
			try {
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Calculating SHA-256 of input image...");
				byte[] in_sha = FileBuffer.getFileHash("SHA-256", input_path);
				String shastr = FileUtils.bytes2str(in_sha);
				shastr = shastr.toUpperCase();
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Hash: " + shastr);
				
				//Check for match
				boolean found = false;
				for(int i = 0; i < checksums.length; i++) {
					if(checksums[i][0] == null) continue;
					if(checksums[i][2] == null) continue;
					if(!checksums[i][0].equalsIgnoreCase("ISO")) continue;
					if(checksums[i][2].equals(shastr)) {
						MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
								"Image match found: " + checksums[i][1]);
						found = true;
						break;
					}
				}
				
				if(!found) {
					MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
							"WARNING: No match was found to known image. Unpack will continue, but be aware that the results will be unpredictable.");
				}
				
			} catch (NoSuchAlgorithmException e) {
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Input file hash failed: internal error (see stack trace)");
				e.printStackTrace();
			} catch (IOException e) {
				MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
						"Input file hash failed: I/O error (see stack trace)");
				e.printStackTrace();
			}
		}
		else {
			MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
					"Checksum table could not be loaded. Skipping input image checksum...");
		}
		
		//Attempt to read CD image
		ISOXAImage cd_image = MyuArcCommon.readISOFile(input_path);
		
		//start output XML for CD build
		if(xml_path == null) {
			xml_path = input_dir + File.separator + "buildiso.xml";
			MyuPackagerLogger.logMessage("Main.main_isoUnpack", 
					"Output XML path was not provided. Setting to \"" + xml_path + "\"");
		}
		LiteNode cdbuild_root = new LiteNode();
		cdbuild_root.name = MyupkgConstants.XML_NODENAME_ISOBUILD;
		
		//Generate relative paths for XML use
		String cddir_rel = MyuArcCommon.localPath2UnixRel(xml_path, output_dir_cd);
		String assetdir_rel = MyuArcCommon.localPath2UnixRel(xml_path, output_dir_asset);
		
		//Get metadata from CD
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_VOLUMEID, cd_image.getVolumeIdent());
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_PUBID, cd_image.getPublisherIdent());
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_REGION, "J");
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_MATCHMODE, "True");
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_FAKETIME, MyuArcCommon.datetime2XMLVal(cd_image.getDateCreated()));
		
		//Extract PSLogo sectors (5-11)
		FileBuffer pslogo = new MultiFileBuffer(7);
		for(int i = 0; i < 7; i++) {
			pslogo.addToFile(cd_image.getSectorData(i+5));
		}
		String pslogo_path = output_dir_cd + File.separator + MyupkgConstants.FILENAME_PSLOGO;
		pslogo.writeFile(pslogo_path);
		pslogo.dispose();
		LiteNode childnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PSLOGO);
		childnode.value = cddir_rel + "/" + MyupkgConstants.FILENAME_PSLOGO;
		
		//Note path tables
		int ptstart = cd_image.getPathTable_1_start();
		LiteNode ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		ptstart = cd_image.getPathTable_2_start();
		ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		ptstart = cd_image.getPathTable_3_start();
		ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		ptstart = cd_image.getPathTable_4_start();
		ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		
		//Get file table from CD (need to annotate file locations and metadata)
		//Extract sectors that are non-zero and not used by the filesystem(?)
		//Render file tree from CD and extract those files to cd out dir (note that track 2 might not be where it's supposed to be - CUE sheet can specify if present)
		
		//Run the arc extractors on the archives and streams. (Exe disassembly is handled externally)
		//Clean up ARC bins in cddir that are successfully broken down
		
	}
	
	private static void main_arcUnpack(Map<String, String> args, String rel_path) throws IOException{
		String input_path = args.get("input");
		String output_dir = args.get("output");
		String spec_path = args.get("arcspec");
		String xml_path = args.get("xmlout");
		int flags = 0;
		
		if(args.containsKey("png")) flags |= MyupkgConstants.PKGR_FLAG_PNGOUT;
		
		//Check path existences
		if(input_path == null){
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Input file argument is required!");
			return;
		}
		if(!FileBuffer.fileExists(input_path)){
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Input file \"" + input_path + "\" does not exist!");
			return;
		}
		
		MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
				"input_path = " + input_path);
		MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
				"output_dir = " + output_dir);
		MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
				"spec_path = " + spec_path);
		MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
				"xml_path = " + xml_path);
		
		if(!FileBuffer.directoryExists(output_dir)) {
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Output directory did not exist. Creating...");
			Files.createDirectories(Paths.get(output_dir));
		}

		//Read offset table
		FileBuffer arcdata = FileBuffer.createBuffer(input_path, false);
		long[] offset_table = MyuArcCommon.readOffsetTable(arcdata);
		int file_count = offset_table.length;
		
		//If spec does not exist, will just assume...
		// 	1. Not XA stream
		//	2. File type unknown
		//	3. Not compressed
		String arcname = null;
		boolean idx_flag = true;
		boolean audio_flag = false;
		boolean xa_a_flag = false;
		boolean xa_v_flag = false;
		boolean lz_flag = false;
		ArrayList<LiteNode> import_files = new ArrayList<LiteNode>(file_count+1);
		
		if(arcname == null){
			arcname = input_path.substring(input_path.lastIndexOf(File.separatorChar) + 1);
			arcname = arcname.replace("D_", "");
			arcname = arcname.substring(0, arcname.indexOf('.'));
		}
		String arcname_c = StringUtils.capitalize(arcname.toLowerCase());
		
		MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
				"Importing archive specs...");
		if(FileBuffer.fileExists(spec_path)){
			LiteNode arcspec = MyuArcCommon.readXML(spec_path);
			arcname = arcspec.attr.get("Name");
			
			arcname_c = StringUtils.capitalize(arcname.toLowerCase());
			if(arcspec != null && arcspec.children != null){
				for(LiteNode child : arcspec.children){
					if(child.name.equals("ArcFile")) import_files.add(child);
				}
			}
			String aval = arcspec.attr.get(MyupkgConstants.XML_ATTR_INDEXTYPE);
			if(aval != null && aval.equalsIgnoreCase("offset")){
				idx_flag = false;
			}
			aval = arcspec.attr.get(MyupkgConstants.XML_ATTR_HASAUDIO);
			if(aval != null && aval.equalsIgnoreCase("true")){
				audio_flag = true;
			}
			aval = arcspec.attr.get(MyupkgConstants.XML_ATTR_ASTREAM);
			if(aval != null && aval.equalsIgnoreCase("true")){
				xa_a_flag = true;
			}
			aval = arcspec.attr.get(MyupkgConstants.XML_ATTR_VSTREAM);
			if(aval != null && aval.equalsIgnoreCase("true")){
				xa_v_flag = true;
			}
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Archive spec xml read!");
		}
		else{
			for(int i = 0; i < file_count; i++){
				LiteNode child = new LiteNode();
				import_files.add(child);
				child.name = "ArcFile";
				child.attr.put(MyupkgConstants.XML_ATTR_FILENAME, String.format("%s_%03d", arcname.toUpperCase(), i));
				child.attr.put(MyupkgConstants.XML_ATTR_ENUM, String.format("%s_%03d", arcname_c, i));
				child.attr.put(MyupkgConstants.XML_ATTR_FILETYPE, MyupkgConstants.FTYPE_UNK);
				child.attr.put(MyupkgConstants.XML_ATTR_LZCOMP, "False");
			}
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Archive spec xml was not provided. Using default settings.");
		}
		
		//Process arcname
		if(arcname.length() > 8){
			arcname = arcname.substring(0,8);
		}
		
		LiteNode export_root = new LiteNode();
		export_root.name = "MyuArchive";
		export_root.attr.put("Name", arcname.toUpperCase());
		export_root.attr.put("Enum", "D_" + arcname_c);
		export_root.attr.put(MyupkgConstants.XML_ATTR_ACCBYFI, StringUtils.capitalize(Boolean.toString(idx_flag)));
		export_root.attr.put(MyupkgConstants.XML_ATTR_HASAUDIO, StringUtils.capitalize(Boolean.toString(audio_flag)));
		export_root.attr.put(MyupkgConstants.XML_ATTR_ASTREAM, StringUtils.capitalize(Boolean.toString(xa_a_flag)));
		export_root.attr.put(MyupkgConstants.XML_ATTR_VSTREAM, StringUtils.capitalize(Boolean.toString(xa_v_flag)));
		
		ExportContext ctx = new ExportContext();
		ctx.rel_dir = rel_path;
		ctx.global_flags = flags;
		ctx.output_dir = output_dir;
		for(int i = 0; i < file_count; i++){
			int filesize = 0;
			if(i < (file_count-1)){
				filesize = (int)(offset_table[i+1] - offset_table[i]);
			}
			else{
				filesize = (int)(arcdata.getFileSize() - offset_table[i]);
			}
			
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Processing file " + i + "...");
			
			//Don't forget to add node even if empty.
			LiteNode f_exp = new LiteNode();
			f_exp.parent = export_root;
			export_root.children.add(f_exp);
			
			if(filesize > 0){
				
				ctx.target_in = import_files.get(i);
				ctx.target_out = f_exp;
				
				if(!lz_flag){
					//Check for it.
					String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_LZCOMP);
					if(aval != null && aval.equalsIgnoreCase("True")) lz_flag = true;
				}
				
				TypeHandler handler = TypeHandler.getHandlerFor(ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILETYPE));
				if(handler != null){
					ctx.data = arcdata.createReadOnlyCopy(offset_table[i], offset_table[i] + filesize);
					handler.exportCallback(ctx);
					ctx.data.dispose();
				}
				else{
					MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
							"Handler for file " + i + " could not be determined. File will be replaced with empty node.");
					f_exp.name = "NoFile";
				}
			}
			else f_exp.name = "NoFile";
		}
		
		//Save XML
		export_root.attr.put(MyupkgConstants.XML_ATTR_LZCOMP, StringUtils.capitalize(Boolean.toString(lz_flag)));
		if(xml_path == null){
			xml_path = output_dir + File.separator + arcname.toLowerCase() + ".xml";
		}
		MyuArcCommon.writeXML(xml_path, export_root);
	}
	
	private static void main_glueTracks(Map<String, String> args) throws IOException{
		//TODO
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
				main_arcUnpack(argmap, "");
			}
			else if(tool.equalsIgnoreCase(TOOLNAME_UNPACK_ISO)){
				//TODO
			}
			
			//Close log
			MyuPackagerLogger.closeLog();
			
		}catch(Exception ex){
			ex.printStackTrace();
			System.exit(1);
		}
		
	}

}
