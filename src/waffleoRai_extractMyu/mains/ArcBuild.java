package waffleoRai_extractMyu.mains;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.StringUtils;
import waffleoRai_extractMyu.ImportContext;
import waffleoRai_extractMyu.LiteNode;
import waffleoRai_extractMyu.LzLitZip;
import waffleoRai_extractMyu.MdecMovieHandler;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.MyupkgConstants;
import waffleoRai_extractMyu.TypeHandler;
import waffleoRai_extractMyu.XAAudioHandler;

public class ArcBuild {
	
	public static final String SYMNAME_BGM_EXTRA = "_resbgm_dat_800e89a8";
	
	//Maybe actually instance this instead of creating a "context", C-brained idiot
	private String xml_spec_path;
	private String working_dir; //Paths are relative to this. Usually the path containing the xml_spec
	private String output_path; //Path to output archive bin
	
	//Header also defines flags to use for loading this file as a macro and has the declaration of the offset table
	private String header_path; //Path to output .h file to (defines the enum value to access these files)
	private String c_path; //Path to output the offset table bin or c? (which gets included in the exe)
	
	private LiteNode specs;
	private boolean isStream;
	private int lzMode;
	private boolean matchFlag;
	private LzLitZip litTableZip;
	
	//Remember that number of assets held must round up to a multiple of 4 for the sake of the offset table.
	private int[] offset_table;
	private int[] decsize_table;
	private String[] enum_table;
	private int current_record;
	private int wpos;
	
	private boolean checkArgs() {
		if(xml_spec_path == null){
			MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
					"XML specification file is required as input!");
			return false;
		}
		if(!FileBuffer.fileExists(xml_spec_path)){
			MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
					"Input file \"" + xml_spec_path + "\" does not exist!");
			return false;
		}
		
		if(output_path == null){
			MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
					"Output file path is required!");
			return false;
		}
		
		MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
				"xml_spec_path = " + xml_spec_path);
		
		//Derive working dir from xml path
		int lastslash = xml_spec_path.lastIndexOf(File.separatorChar);
		if(lastslash >= 0) {
			working_dir = xml_spec_path.substring(0, lastslash);
		}
		else working_dir = "";
		MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
				"working_dir = " + working_dir);
		
		//Outputs (make dirs if needed)
		//.c and .h paths are not required, but the arc out is.
		MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
				"output_path = " + output_path);
		MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
				"header_path = " + header_path);
		MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
				"c_path = " + c_path);
		
		MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
				"matchFlag = " + matchFlag);
		
		try {
			lastslash = output_path.lastIndexOf(File.separatorChar);
			if(lastslash >= 0) {
				String dir = output_path.substring(0, lastslash);
				if(!FileBuffer.directoryExists(dir)) {
					MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
							"Creating output directory: " + dir);
					Files.createDirectories(Paths.get(dir));
				}
			}
			
			if(header_path != null) {
				lastslash = header_path.lastIndexOf(File.separatorChar);
				if(lastslash >= 0) {
					String dir = header_path.substring(0, lastslash);
					if(!FileBuffer.directoryExists(dir)) {
						MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
								"Creating output directory: " + dir);
						Files.createDirectories(Paths.get(dir));
					}
				}
			}
			
			if(c_path != null) {
				lastslash = c_path.lastIndexOf(File.separatorChar);
				if(lastslash >= 0) {
					String dir = c_path.substring(0, lastslash);
					if(!FileBuffer.directoryExists(dir)) {
						MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
								"Creating output directory: " + dir);
						Files.createDirectories(Paths.get(dir));
					}
				}
			}
			
		}
		catch(IOException ex) {
			MyuPackagerLogger.logMessage("ArcBuild.checkArgs", 
					"Failed to create one or more output directories!");
			ex.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	private static int fmtHexDigitCount(int[] values) {
		if(values == null) return 0;
		
		int max = 0;
		for(int i = 0; i < values.length; i++) {
			if(values[i] > max) {
				max = values[i];
			}
		}
		
		int dig = 0;
		for(int i = 0; i < 8; i++) {
			if(max != 0) dig++;
			max >>>= 4;
		}
		
		return dig;
	}
	
	private int importArcAsset(LiteNode asset_specs, String inpath, OutputStream out, long outpos) {
		//Returns size of asset as it will be packaged.
		String asset_type = asset_specs.name;
		TypeHandler handler = TypeHandler.getHandlerFor(asset_type);
		if(handler == null) {
			MyuPackagerLogger.logMessage("ArcBuild.importArcAsset", 
					"Couldn't find type handler \"" + inpath + "\"! Skipping...");
			return 0;
		}
		
		ImportContext ictx = new ImportContext();
		ictx.lzMode = lzMode;
		ictx.import_specs = asset_specs;
		ictx.output = out;
		ictx.outpos = outpos;
		ictx.decompSize = -1;
		ictx.matchFlag = matchFlag;
		ictx.wd = working_dir;
		ictx.litTableZip = litTableZip;
		ictx.indexInArc = current_record;
		
		asset_specs.value = inpath;
		
		int wsize = handler.importCallback(ictx);
		if(lzMode > 0) {
			decsize_table[current_record] = ictx.decompSize;
		}
		return wsize;
	}
	
	private boolean packageRegularArchiveData() throws IOException {
		//Figure out how many file slots we need
		int slots = specs.children.size();
		slots = (slots + 3) & ~0x3;
		offset_table = new int[slots];
		decsize_table = new int[slots];
		enum_table = new String[slots];
		current_record = 0;
		wpos = slots << 2;
		
		String arcenum = specs.attr.get(MyupkgConstants.XML_ATTR_ENUM);
		if(arcenum == null) arcenum = "AnonArc";
		
		boolean secAlignArc = false;
		String aval = specs.attr.get(MyupkgConstants.XML_ATTR_SECALIGN);
		if(aval != null && aval.equalsIgnoreCase("true")) {
			secAlignArc = true;
		}
		
		if(matchFlag && (lzMode == MyuArcCommon.COMPR_MODE_FFL)) {
			//Open the literal tables.
			aval = specs.attr.get(MyupkgConstants.XML_ATTR_FFLTBLPATH);
			if(aval != null) {
				String fftpath = MyuArcCommon.unixRelPath2Local(xml_spec_path.substring(0, xml_spec_path.lastIndexOf(File.separator)), aval);
				litTableZip = new LzLitZip(fftpath);
			}
		}
		
		String temp_path = output_path + ".dat.tmp";
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(temp_path));
		for(LiteNode child : specs.children) {
			String cval = child.value;
			offset_table[current_record] = wpos;
			if(child.name.equalsIgnoreCase("nofile")) {
				current_record++;
				continue;
			}
			
			String ename = child.attr.get(MyupkgConstants.XML_ATTR_ENUM);
			if(ename == null) {
				ename = arcenum + String.format("anon%03d", current_record);
			}
			enum_table[current_record] = ename;
			
			MyuPackagerLogger.logMessage("ArcBuild.packageRegularArchiveData", 
					"Working on " + ename + "...");
			
			wpos += importArcAsset(child, MyuArcCommon.unixRelPath2Local(working_dir, cval), bos, wpos);
			
			//Pad to CD sector if requested.
			aval = child.attr.get(MyupkgConstants.XML_ATTR_SECALIGN);
			if(aval != null) {
				if(aval.equalsIgnoreCase("true")) {
					while((wpos & 0x7ff) != 0) {
						bos.write(Byte.toUnsignedInt(MyupkgConstants.PADDING_BYTE));
						wpos++;
					}
				}
			}
			else {
				if(secAlignArc) {
					while((wpos & 0x7ff) != 0) {
						bos.write(Byte.toUnsignedInt(MyupkgConstants.PADDING_BYTE));
						wpos++;
					}
				}
			}

			child.value = cval;
			current_record++;
		}
		bos.close();
		
		//Add offset table
		int tpos = 0;
		FileBuffer offtbl = new FileBuffer(slots << 2, false);
		for(int i = 0; i < slots; i++) {
			offtbl.addToFile(offset_table[i] - tpos);
			tpos += 4;
		}
		
		bos = new BufferedOutputStream(new FileOutputStream(output_path));
		offtbl.writeToStream(bos);
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(temp_path));
		int b = -1;
		while((b = bis.read()) != -1) bos.write(b);
		bis.close();
		bos.close();
		
		Files.delete(Paths.get(temp_path));
		
		return true;
	}

	private boolean outputRegularArchiveTables() throws IOException {
		String arcname = specs.attr.get(MyupkgConstants.XML_ATTR_NAME);
		if(arcname == null) {
			MyuPackagerLogger.logMessage("ArcBuild.outputRegularArchiveTables", 
					"Archive name is required!");
			return false;
		}
		arcname = StringUtils.capitalize(arcname.toLowerCase());
		
		String posTableName = "gFilePos" + arcname;
		String sizeTableName = "gFileSize" + arcname;
		String enumDefName = arcname + "ResFile";
		
		//.h file
		if(header_path != null) {
			String hdef_name = "RESARC_" + arcname.toUpperCase() + "_H";
			BufferedWriter bw = new BufferedWriter(new FileWriter(header_path));
			bw.write("#ifndef " + hdef_name + "\n");
			bw.write("#define " + hdef_name + "\n\n");
			
			bw.write("/* ---------------------------------------------\n");
			bw.write("*  Autogenerated by MyuPackager arcpack\n");
			bw.write("*  ---------------------------------------------*/\n\n");
			
			bw.write("#include \"psx/PSXTypes.h\"\n\n");
			bw.write("#ifdef __cplusplus\n");
			bw.write("extern \"C\" {\n");
			bw.write("#endif\n\n");
			
			bw.write("typedef enum {\n");
			for(int i = 0; i < enum_table.length; i++) {
				if(enum_table[i] == null || enum_table[i].isEmpty()) continue;
				bw.write("\t/*");
				bw.write(String.format("%04d", i));
				bw.write("*/\t");
				bw.write(enum_table[i]);
				if(i == 0 || enum_table[i-1] == null || enum_table[i-1].isEmpty()) {
					bw.write(" = " + i);
				}
				bw.write(",\n");
			}
			bw.write("} " + enumDefName + ";\n\n");
			
			bw.write("extern uint32_t " + posTableName + "[];\n");
			if(lzMode > 0) {
				bw.write("extern uint32_t " + sizeTableName + "[];\n");
			}
			
			bw.write("\n#ifdef __cplusplus\n");
			bw.write("}\n");
			bw.write("#endif\n\n");
			
			bw.write("\n#endif\n");
			bw.close();
		}
		
		//.c file
		if(c_path != null) {
			String h_name = header_path;
			if(h_name.contains(File.separator)) {
				h_name = h_name.substring(h_name.lastIndexOf(File.separator) + 1);
			}
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(c_path));
			bw.write("/* ---------------------------------------------\n");
			bw.write("*  Autogenerated by MyuPackager arcpack\n");
			bw.write("*  ---------------------------------------------*/\n");
			bw.write("\n#include \"res/" + h_name + "\"\n\n");
			
			bw.write("#ifdef __cplusplus\n");
			bw.write("extern \"C\" {\n");
			bw.write("#endif\n\n");
			
			int fmtDigits = fmtHexDigitCount(offset_table);
			
			int tpos = 0;
			int lastOffset = 0;
			bw.write("uint32_t " + posTableName + "[] = {\n");
			for(int i = 0; i < offset_table.length; i+=8) {
				bw.write("\t/*" + String.format("%04d", i) + "*/\t");
				for(int j = 0; j < 8; j++) {
					if(j >= offset_table.length) break;
					int ii = i+j;
					lastOffset = offset_table[ii] - tpos;
					bw.write(String.format("0x%0" + fmtDigits + "x", lastOffset));
					if(ii < (offset_table.length-1)) {
						bw.write(", ");
					}
					tpos += 4;
				}
				bw.write("\n");
			}
			bw.write("};\n");
			
			if(matchFlag && arcname.equals("Bgm")) {
				//For some reason, the first 0x140 of archive data after table are copied to the exe.
				//Why? 
				FileBuffer bgmdat = FileBuffer.createBuffer(output_path, 0x100, 0x240);
				bw.write("\nstatic uint8_t " + SYMNAME_BGM_EXTRA + "[] = {\n");
				for(int i = 0; i < 0x140; i+=16) {
					bw.write("\t/*" + String.format("%04d", i) + "*/\t");
					for(int j = 0; j < 16; j++) {
						if(j >= 0x140) break;
						int ii = i+j;
						bw.write(String.format("0x%02x", bgmdat.getByte(ii)));
						if(ii < 0x13f) {
							bw.write(", ");
						}
					}
					bw.write("\n");
				}
				bw.write("};\n");
			}
			
			//These are DECOMPRESSED!
			if(lzMode > 0) {
				//Fill in zeroes with one AHEAD (again... for some reason)
				if(matchFlag) {
					for(int j = (decsize_table.length - 2); j >= 0; j--) {
						if(decsize_table[j] == 0) decsize_table[j] = decsize_table[j+1];
					}
				}
				
				fmtDigits = fmtHexDigitCount(decsize_table);
				bw.write("\nuint32_t " + sizeTableName + "[] = {\n");
				for(int i = 0; i < decsize_table.length; i+=8) {
					bw.write("\t/*" + String.format("%04d", i) + "*/\t");
					for(int j = 0; j < 8; j++) {
						if(j >= decsize_table.length) break;
						int ii = i+j;
						if(ii == decsize_table.length - 1) {
							if(matchFlag && (decsize_table[ii] == 0)) {
								//Write the last offset instead (for some reason)
								bw.write(String.format("0x%0" + fmtDigits + "x", lastOffset));
							}
							else bw.write(String.format("0x%0" + fmtDigits + "x", decsize_table[ii]));
						}
						else {
							bw.write(String.format("0x%0" + fmtDigits + "x, ", decsize_table[ii]));
						}
					}
					bw.write("\n");
				}
				bw.write("};\n");
			}
			
			bw.write("\n#ifdef __cplusplus\n");
			bw.write("}\n");
			bw.write("#endif\n\n");
			
			bw.close();
		}
		
		return true;
	}
	
	private void packageXAStream() throws IOException, UnsupportedFileTypeException {
		String aval = specs.attr.get(MyupkgConstants.XML_ATTR_VSTREAM);
		if((aval != null) && (aval.equalsIgnoreCase("true"))) {
			MdecMovieHandler vhandle = new MdecMovieHandler();
			vhandle.import_spec = specs;
			vhandle.wd = working_dir;
			
			MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
					"Building A/V stream file...");
			vhandle.buildStream(output_path);
			
			String hpath_local = null;
			if(header_path != null) {
				MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
						"Generating .h file(s)...");
				
				String hDir = MyuArcCommon.getContainingDir(header_path);
				String inclDir = hDir;
				int inclpos = inclDir.lastIndexOf("include");
				if(inclpos >= 0) {
					inclDir = inclDir.substring(0, inclpos + 7);
				}
				vhandle.exportH(header_path);
				
				hpath_local = MyuArcCommon.localPath2UnixRel(inclDir, header_path);
				if(hpath_local.startsWith("./")) hpath_local = hpath_local.substring(2);
			}
			else {
				hpath_local = "res/ResMovie.h";
			}
			
			if(c_path != null) {
				MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
						"Generating .c file(s)...");
				
				vhandle.exportC(c_path, hpath_local);
			}
			
		}
		else {
			aval = specs.attr.get(MyupkgConstants.XML_ATTR_ASTREAM);
			if((aval != null) && (aval.equalsIgnoreCase("true"))) {
				XAAudioHandler ahandle = new XAAudioHandler();
				ahandle.wd = working_dir;
				ahandle.import_spec = specs;
				
				MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
						"Building stream file...");
				ahandle.buildStream(output_path);
				
				String hpath_local = null;
				if(header_path != null) {
					MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
							"Generating .h file(s)...");
					
					String hDir = MyuArcCommon.getContainingDir(header_path);
					String inclDir = hDir;
					int inclpos = inclDir.lastIndexOf("include");
					if(inclpos >= 0) {
						inclDir = inclDir.substring(0, inclpos + 7);
					}
					ahandle.exportH(inclDir, header_path, hDir + File.separator + "xaaud");
					
					hpath_local = MyuArcCommon.localPath2UnixRel(inclDir, header_path);
				}
				else {
					hpath_local = "res/ResVoice.h";
				}
				
				if(c_path != null) {
					MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
							"Generating .c file(s)...");
					
					ahandle.exportC(c_path, hpath_local);
				}
				
			}
			else {
				MyuPackagerLogger.logMessage("ArcBuild.packageXAStream", 
						"ERROR: Only A/V or audio streams supported!");
			}
		}
	}
	
	private boolean tryPackArchive() {
		try {
			specs = MyuArcCommon.readXML(xml_spec_path);
			String aval = specs.attr.get(MyupkgConstants.XML_ATTR_ISXASTR);
			if(aval != null && aval.equalsIgnoreCase("true")) {
				//XA stream
				isStream = true;
			}
			else isStream = false;
			
			aval = specs.attr.get(MyupkgConstants.XML_ATTR_LZCOMP);
			if(aval != null) {
				if(aval.equals(MyupkgConstants.COMP_TYPE_NONE)) lzMode = -1;
				else if(aval.equals(MyupkgConstants.COMP_TYPE_FAST)) lzMode = MyuArcCommon.COMPR_MODE_FASTEST;
				else if(aval.equals(MyupkgConstants.COMP_TYPE_BEST)) lzMode = MyuArcCommon.COMPR_MODE_SMALL;
				else if(aval.equals(MyupkgConstants.COMP_TYPE_FASTFORCE)) lzMode = MyuArcCommon.COMPR_MODE_FFL;
			}
			else lzMode = -1;
			
			if(isStream) {
				packageXAStream();
			}
			else {
				//Regular archive
				if(!packageRegularArchiveData()) {
					MyuPackagerLogger.logMessage("ArcBuild.tryPackArchive", 
							"Regular archive packaging failed!");
					return false;
				}
				if(!outputRegularArchiveTables()) {
					MyuPackagerLogger.logMessage("ArcBuild.tryPackArchive", 
							"Archive info table generation failed!");
					return false;
				}
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	//---------------------
	
	public static void main_arcPack(Map<String, String> args) {
		ArcBuild ctx = new ArcBuild();
		ctx.xml_spec_path = args.get("arcspec");
		ctx.output_path = args.get("output");
		ctx.header_path = args.get("hout");
		ctx.c_path = args.get("cout");
		
		ctx.matchFlag = args.containsKey("match");
		
		if (!ctx.checkArgs()) return;
		
		if(ctx.tryPackArchive()) {
			MyuPackagerLogger.logMessage("ArcBuild.main_arcPack", 
					"Archive packaging returned no error!");
		}
		else {
			MyuPackagerLogger.logMessage("ArcBuild.main_arcPack", 
					"Archive packaging failed! See log for details.");
		}
		
	}

}
