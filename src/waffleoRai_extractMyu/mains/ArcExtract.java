package waffleoRai_extractMyu.mains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.StringUtils;
import waffleoRai_extractMyu.ExportContext;
import waffleoRai_extractMyu.LiteNode;
import waffleoRai_extractMyu.MdecMovieHandler;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.MyupkgConstants;
import waffleoRai_extractMyu.TypeHandler;
import waffleoRai_extractMyu.XAAudioHandler;

public class ArcExtract {
	
	public static class ArcExtractContext{
		public String input_path;
		public String output_dir;
		public String spec_path;
		public String xml_path;
		public int flags;
		public String rel_path;
		public LiteNode arcspec;
		
		public PSXXAStream stream; //Only for XA
		//public FileBuffer inputFile; //Only for XA
	}
	
	private static boolean checkArgs(ArcExtractContext ctx) throws IOException {
		if(ctx.input_path == null){
			MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
					"Input file argument is required!");
			return false;
		}
		if(!FileBuffer.fileExists(ctx.input_path)){
			MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
					"Input file \"" + ctx.input_path + "\" does not exist!");
			return false;
		}
		
		MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
				"input_path = " + ctx.input_path);
		MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
				"output_dir = " + ctx.output_dir);
		MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
				"spec_path = " + ctx.spec_path);
		MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
				"xml_path = " + ctx.xml_path);
		
		if(!FileBuffer.directoryExists(ctx.output_dir)) {
			MyuPackagerLogger.logMessage("ArcExtract.checkArgs", 
					"Output directory did not exist. Creating...");
			Files.createDirectories(Paths.get(ctx.output_dir));
		}
		
		return true;
	}
	
	private static void unpackXAClipGroup(ArcExtractContext actx, LiteNode node, LiteNode outParent) throws IOException, UnsupportedFileTypeException {
		if(node.name == null) return;
		if(!node.name.equals(MyupkgConstants.ASSET_TYPE_CLIPGROUP)) return;
		
		LiteNode xmlOut = outParent.newChild(MyupkgConstants.ASSET_TYPE_CLIPGROUP);
		for(Entry<String, String> entry : node.attr.entrySet()) {
			xmlOut.attr.put(entry.getKey(), entry.getValue());
		}
		
		String groupName = node.attr.get(MyupkgConstants.XML_ATTR_NAME);
		if(groupName == null) {
			groupName = String.format("CLIPGROUP_%04X", outParent.children.size() - 1);
			xmlOut.attr.put(MyupkgConstants.XML_ATTR_FILENAME, groupName);
		}
		MyuPackagerLogger.logMessage("ArcExtract.unpackXAClipGroup", 
				"Working on clip group " + groupName + "...");
		
		String groupOutDir = actx.output_dir + File.separator + groupName;
		if(!FileBuffer.directoryExists(groupOutDir)) {
			Files.createDirectories(Paths.get(groupOutDir));
		}
		
		ExportContext ctx = new ExportContext();
		ctx.rel_dir = actx.rel_path + "/" + groupOutDir;
		ctx.global_flags = actx.flags;
		ctx.output_dir = groupOutDir;
		ctx.secAlignMode = 0;
		if(actx.arcspec != null) {
			if(actx.spec_path != null) {
				ctx.arcspec_wd = MyuArcCommon.getContainingDir(actx.spec_path);
			}
		}
		if(actx.xml_path != null) {
			ctx.xml_wd = MyuArcCommon.getContainingDir(actx.xml_path);
		}
		
		ctx.xaStr = actx.stream;
		
		for(LiteNode child : node.children) {
			if(child.name == null) continue;
			ctx.target_in = child;
			ctx.target_out = xmlOut.newChild(child.name);
			
			if(child.name.equals(MyupkgConstants.FTYPE_XAAUDIO)) {
				ctx.target_out.name = MyupkgConstants.ASSET_TYPE_XAAUDIO;
				XAAudioHandler.export(ctx);
			}
			else if(child.name.equals(MyupkgConstants.FTYPE_XAMOVIE)) {
				ctx.target_out.name = MyupkgConstants.ASSET_TYPE_XAMOVIE;
				MdecMovieHandler.export(ctx);
			}
			else {
				MyuPackagerLogger.logMessage("ArcExtract.unpackXAClipGroup", 
						"File type \"" + child.name + "\" not recognized. Skipping...");
			}
		}
		
	}
	
	public static void unpackTrueArchive(ArcExtractContext actx) throws IOException{
		//Read offset table
		FileBuffer arcdata = FileBuffer.createBuffer(actx.input_path, false);
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
		int lz_mode = -1;
		//boolean sztbl_flag = false;
		int forceSecAlign = 0; //False
		ArrayList<LiteNode> import_files = new ArrayList<LiteNode>(file_count+1);
		
		if(arcname == null){
			arcname = MyuArcCommon.getFilename(actx.input_path);
			arcname = arcname.replace("D_", "");
			if(arcname.contains(".")) {
				arcname = arcname.substring(0, arcname.indexOf('.'));
			}
		}
		String arcname_c = StringUtils.capitalize(arcname.toLowerCase());
		String fftpath = null;
		
		MyuPackagerLogger.logMessage("ArcExtract.unpackTrueArchive", 
				"Importing archive specs...");
		if(FileBuffer.fileExists(actx.spec_path)){
			arcname = actx.arcspec.attr.get("Name");
			
			arcname_c = StringUtils.capitalize(arcname.toLowerCase());
			if(actx.arcspec != null && actx.arcspec.children != null){
				for(LiteNode child : actx.arcspec.children){
					if(child.name.equals("ArcFile")) import_files.add(child);
				}
			}
			String aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_INDEXTYPE);
			if(aval != null && aval.equalsIgnoreCase("offset")){
				idx_flag = false;
			}
			aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_HASAUDIO);
			if(aval != null && aval.equalsIgnoreCase("true")){
				audio_flag = true;
			}
			aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_ASTREAM);
			if(aval != null && aval.equalsIgnoreCase("true")){
				xa_a_flag = true;
			}
			aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_VSTREAM);
			if(aval != null && aval.equalsIgnoreCase("true")){
				xa_v_flag = true;
			}
			MyuPackagerLogger.logMessage("ArcExtract.unpackTrueArchive", 
					"Archive spec xml read!");
			
			aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_SECALIGN);
			if(aval != null){
				if(aval.equalsIgnoreCase("true")) {
					forceSecAlign = 1;
				}
				else if(aval.equalsIgnoreCase("partial")) {
					forceSecAlign = -1;
				}
			}
			
			aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_FFLTBLPATH);
			if(aval != null){
				String specdir = MyuArcCommon.getContainingDir(actx.spec_path);
				fftpath = MyuArcCommon.unixRelPath2Local(specdir, aval);
				fftpath = MyuArcCommon.localPath2UnixRel(actx.xml_path, fftpath);
			}
			
		}
		else{
			for(int i = 0; i < file_count; i++){
				LiteNode child = new LiteNode();
				import_files.add(child);
				child.name = "ArcFile";
				child.attr.put(MyupkgConstants.XML_ATTR_FILENAME, String.format("%s_%03d", arcname.toUpperCase(), i));
				child.attr.put(MyupkgConstants.XML_ATTR_ENUM, String.format("%s_%03d", arcname_c, i));
				child.attr.put(MyupkgConstants.XML_ATTR_FILETYPE, MyupkgConstants.FTYPE_UNK);
				child.attr.put(MyupkgConstants.XML_ATTR_LZCOMP, "None");
			}
			MyuPackagerLogger.logMessage("ArcExtract.unpackTrueArchive", 
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
		if(fftpath != null) export_root.attr.put(MyupkgConstants.XML_ATTR_FFLTBLPATH, fftpath);
		
		String salignStr = "False";
		if(forceSecAlign == 1) {
			salignStr = "True";
		}
		else if(forceSecAlign == -1) {
			salignStr = "Partial";
		}
		export_root.attr.put(MyupkgConstants.XML_ATTR_SECALIGN, salignStr);
		
		ExportContext ctx = new ExportContext();
		ctx.rel_dir = actx.rel_path;
		ctx.global_flags = actx.flags;
		ctx.output_dir = actx.output_dir;
		ctx.secAlignMode = forceSecAlign;
		if(actx.arcspec != null) {
			if(actx.spec_path != null) {
				ctx.arcspec_wd = MyuArcCommon.getContainingDir(actx.spec_path);
			}
		}
		if(actx.xml_path != null) {
			ctx.xml_wd = MyuArcCommon.getContainingDir(actx.xml_path);
		}
		for(int i = 0; i < file_count; i++){
			int filesize = 0;
			if(i < (file_count-1)){
				filesize = (int)(offset_table[i+1] - offset_table[i]);
			}
			else{
				filesize = (int)(arcdata.getFileSize() - offset_table[i]);
			}
			
			MyuPackagerLogger.logMessage("ArcExtract.unpackTrueArchive", 
					"Processing file " + i + "...");
			
			//Don't forget to add node even if empty.
			LiteNode f_exp = new LiteNode();
			f_exp.parent = export_root;
			export_root.children.add(f_exp);
			
			if(filesize > 0){
				
				ctx.target_in = import_files.get(i);
				ctx.target_out = f_exp;
				
				String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_SECALIGN);
				if(aval != null) {
					ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_SECALIGN, aval);
				}
				else {
					ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_SECALIGN, "False");
				}
				
				aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_LFORCE);
				if(aval != null) {
					ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_LFORCE, aval);
				}
				
				if(lz_mode <= 0){
					//Check for it.
					aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_LZCOMP);
					if(aval != null) {
						if(aval.equals(MyupkgConstants.COMP_TYPE_FAST)) lz_mode = MyuArcCommon.COMPR_MODE_FASTEST;
						else if(aval.equals(MyupkgConstants.COMP_TYPE_BEST)) lz_mode = MyuArcCommon.COMPR_MODE_SMALL;
						else if(aval.equals(MyupkgConstants.COMP_TYPE_FASTFORCE)) lz_mode = MyuArcCommon.COMPR_MODE_FFL;
					}
				}
				
				TypeHandler handler = TypeHandler.getHandlerFor(ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILETYPE));
				if(handler != null){
					ctx.data = arcdata.createReadOnlyCopy(offset_table[i], offset_table[i] + filesize);
					handler.exportCallback(ctx);
					ctx.data.dispose();
				}
				else{
					MyuPackagerLogger.logMessage("ArcExtract.unpackTrueArchive", 
							"Handler for file " + i + " could not be determined. File will be replaced with empty node.");
					f_exp.name = "NoFile";
				}
			}
			else f_exp.name = "NoFile";
		}
		
		//Save XML
		String lzstr = MyupkgConstants.COMP_TYPE_NONE;
		switch(lz_mode) {
		case MyuArcCommon.COMPR_MODE_FASTEST: lzstr = MyupkgConstants.COMP_TYPE_FAST; break;
		case MyuArcCommon.COMPR_MODE_SMALL: lzstr = MyupkgConstants.COMP_TYPE_BEST; break;
		case MyuArcCommon.COMPR_MODE_FFL: lzstr = MyupkgConstants.COMP_TYPE_FASTFORCE; break;
		}
		export_root.attr.put(MyupkgConstants.XML_ATTR_LZCOMP, lzstr);
		if(actx.xml_path == null){
			actx.xml_path = actx.output_dir + File.separator + arcname.toLowerCase() + ".xml";
		}
		MyuArcCommon.writeXML(actx.xml_path, export_root);
	}
	
	public static void unpackXAStream(ArcExtractContext actx) throws IOException, UnsupportedFileTypeException{
		if(!FileBuffer.fileExists(actx.spec_path)){
			MyuPackagerLogger.logMessage("ArcExtract.unpackXAStream", 
					"XA stream cannot be split without spec!");
			return;
		}
		
		LiteNode import_root = actx.arcspec;
		String arcname = import_root.attr.get(MyupkgConstants.XML_ATTR_NAME);
		String enumName = import_root.attr.get(MyupkgConstants.XML_ATTR_ENUM);
		if(enumName == null) {
			enumName = arcname.toLowerCase();
			enumName = StringUtils.capitalize(enumName);
		}
		
		LiteNode export_root = new LiteNode();
		export_root.name = "MyuArchive";
		export_root.attr.put(MyupkgConstants.XML_ATTR_NAME, arcname.toUpperCase());
		export_root.attr.put(MyupkgConstants.XML_ATTR_ENUM, enumName);
		export_root.attr.put(MyupkgConstants.XML_ATTR_ISXASTR, "True");
		
		String aval = import_root.attr.get(MyupkgConstants.XML_ATTR_AUDCH);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_AUDCH, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_IS_STEREO);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_IS_STEREO, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_SAMPLERATE);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_SAMPLERATE, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_BITDEPTH);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_BITDEPTH, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_DBLSPEED);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_DBLSPEED, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_ASTREAM);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_ASTREAM, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_VSTREAM);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_VSTREAM, aval);
		
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_GRP);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_TBLNAME_GRP, aval);
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_CLIP);
		if(aval != null) export_root.attr.put(MyupkgConstants.XML_ATTR_TBLNAME_CLIP, aval);
		
		aval = import_root.attr.get(MyupkgConstants.XML_ATTR_CMERGE);
		if(aval != null) {
			aval = MyuArcCommon.unixRelPath2Local(MyuArcCommon.getContainingDir(actx.spec_path), aval);
			aval = MyuArcCommon.localPath2UnixRel(actx.xml_path, aval);
			export_root.attr.put(MyupkgConstants.XML_ATTR_CMERGE, aval);
		}
		
		ExportContext ctx = new ExportContext();
		ctx.rel_dir = actx.rel_path;
		ctx.global_flags = actx.flags;
		ctx.output_dir = actx.output_dir;
		ctx.secAlignMode = 0;
		if(actx.arcspec != null) {
			if(actx.spec_path != null) {
				ctx.arcspec_wd = MyuArcCommon.getContainingDir(actx.spec_path);
			}
		}
		if(actx.xml_path != null) {
			ctx.xml_wd = MyuArcCommon.getContainingDir(actx.xml_path);
		}
		
		FileNode strnode = new FileNode(null, "xastream");
		strnode.setSourcePath(actx.input_path);
		strnode.setOffset(0L);
		strnode.setLength(FileBuffer.fileSize(actx.input_path));
		actx.stream = PSXXAStream.readStream(strnode);
		ctx.xaStr = actx.stream;
		
		for(LiteNode child : import_root.children) {
			if(child.name == null) continue;
			if(child.name.equals(MyupkgConstants.ASSET_TYPE_CLIPGROUP)) {
				unpackXAClipGroup(actx, child, export_root);
			}
			else if(child.name.equals(MyupkgConstants.FTYPE_XAAUDIO)) {
				ctx.target_in = child;
				ctx.target_out = export_root.newChild(MyupkgConstants.ASSET_TYPE_XAAUDIO);
				XAAudioHandler.export(ctx);
			}
			else if(child.name.equals(MyupkgConstants.FTYPE_XAMOVIE)) {
				ctx.target_in = child;
				ctx.target_out = export_root.newChild(MyupkgConstants.ASSET_TYPE_XAMOVIE);
				MdecMovieHandler.export(ctx);
			}
			else {
				MyuPackagerLogger.logMessage("ArcExtract.unpackXAStream", 
						"Node type \"" + child.name + "\" not recognized! Skipping...");
			}
		}

		MyuArcCommon.writeXML(actx.xml_path, export_root);
	}
	
	public static void unpackArchive(ArcExtractContext actx) throws IOException, UnsupportedFileTypeException{
		if(FileBuffer.fileExists(actx.spec_path)){
			actx.arcspec = MyuArcCommon.readXML(actx.spec_path);
			//See if it's flagged XA stream or not.
			String aval = actx.arcspec.attr.get(MyupkgConstants.XML_ATTR_ISXASTR);
			if(aval != null && (aval.equalsIgnoreCase("true"))) {
				//stream
				unpackXAStream(actx);
			}
			else {
				//arc
				unpackTrueArchive(actx);
			}
		}
		else {
			//Assume it isn't an XA stream.
			unpackTrueArchive(actx);
		}
	}
	
	public static void main_arcUnpack(Map<String, String> args, String rel_path) throws IOException, UnsupportedFileTypeException{
		ArcExtractContext actx = new ArcExtractContext();
		actx.input_path = args.get("input");
		actx.output_dir = args.get("output");
		actx.spec_path = args.get("arcspec");
		actx.xml_path = args.get("xmlout");
		actx.flags = 0;
		
		if(args.containsKey("png")) actx.flags |= MyupkgConstants.PKGR_FLAG_PNGOUT;
		
		if(!checkArgs(actx)) return;
		
		//Determine rel dir (path of output dir relative to output xml)
		actx.rel_path = MyuArcCommon.localPath2UnixRel(actx.xml_path, actx.output_dir);

		unpackArchive(actx);
	}

}
