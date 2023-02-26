package waffleoRai_extractMyu;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;

public class Main {
	
	public static final String TOOLNAME_UNPACK_ISO = "isounpack";
	public static final String TOOLNAME_UNPACK_ARC = "arcunpack";
	
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
		System.err.println("--iso [Path to input CD image]");
		System.err.println("--cue [Path to cue table if input image is a bin]");
		System.err.println("--out [Path to directory to place extracted CD contents]");
		System.err.println("--arcspec [Path to directory containing tables w/ file info for archives (file info, VOICE ptr table etc.)]");
	}
	
	public static void printUsage_ArcUnpack(){
		//Note that it can still run without any specs, but it'll just dump binaries.
		System.err.println("MyuPackager Archive Unpack ---------- ");
		System.err.println("--input\t\t[Path to binary archive/stream file]");
		System.err.println("--output\t\t[Path to output directory to place archive contents]");
		System.err.println("--arcspec\t\t[Path to xml file containing info about arc/stream contents]");
		System.err.println("--xmlout\t\t[Path to xml file output]");
		System.err.println("--log\t\t[Path to log file output (Defaults to stderr)]");
		//System.err.println("-png\t\t[FLAG - If set, also convert any present images to png.]");
		//Spec files are xmls
	}
	
	private static void main_arcUnpack(Map<String, String> args, String rel_path) throws IOException{
		String input_path = args.get("input");
		String output_dir = args.get("output");
		String spec_path = args.get("arcspec");
		String xml_path = args.get("xmlout");
		int flags = 0;
		
		if(args.containsKey("png")) flags |= MyupkgConstants.PKGR_FLAG_PNGOUT;
		
		//Check path existences
		if(!FileBuffer.fileExists(input_path)){
			MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
					"Input file \"" + input_path + "\" does not exist!");
			return;
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
		
		MyuPackagerLogger.logMessage("Main.main_arcUnpack", 
				"Importing archive specs...");
		if(FileBuffer.fileExists(spec_path)){
			LiteNode arcspec = MyuArcCommon.readXML(spec_path);
			arcname = arcspec.attr.get("Name");
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
			arcname = input_path.substring(input_path.lastIndexOf(File.separatorChar));
			arcname = arcname.substring(0, arcname.indexOf('.'));
			String arcname_c = StringUtils.capitalize(arcname);
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
		if(arcname == null){
			arcname = input_path.substring(input_path.lastIndexOf(File.separatorChar));
			arcname = arcname.substring(0, arcname.indexOf('.'));
		}
		if(arcname.length() > 8){
			arcname = arcname.substring(0,8);
		}
		
		LiteNode export_root = new LiteNode();
		export_root.name = "MyuArchive";
		export_root.attr.put("Name", arcname.toUpperCase());
		export_root.attr.put("Enum", "D_" + StringUtils.capitalize(arcname.toLowerCase()));
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
