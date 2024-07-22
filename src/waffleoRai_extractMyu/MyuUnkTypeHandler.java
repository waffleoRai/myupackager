package waffleoRai_extractMyu;

import java.io.File;
import java.io.IOException;

import waffleoRai_Utils.FileBuffer;

public class MyuUnkTypeHandler implements TypeHandler{
	
	public int importCallback(ImportContext ctx){
		if(ctx == null) return 0;
		
		//Get file path (expects to find it in value)
		String filename = ctx.import_specs.value;
		if(filename == null) {
			MyuPackagerLogger.logMessage("MyuUnkTypeHandler.importCallback", "File name is required for import!");
			return 0;
		}
		
		//Since this is just treated as a binary file, we don't need any type specific processing.
		//Compress, if requested.
		try {
			int ret = 0;
			FileBuffer data = FileBuffer.createBuffer(filename, false);
			if(ctx.lzMode > 0) {
				if(ctx.matchFlag) {
					MatchFile mf = new MatchFile();
					mf.data = data;
					mf.bufferGarbageString = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_GARBAGE);

					if(ctx.lzMode == MyuArcCommon.COMPR_MODE_FFL) {
						FileBuffer litTable = null;
						if(ctx.litTableZip != null) {
							String aval = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_LFORCE);
							if(aval != null) {
								int id = Integer.parseInt(aval);
								litTable = ctx.litTableZip.getEntryData(id);
							}
						}
						
						FileBuffer data_comp = MyuArcCommon.lzCompressMatchForceLit(mf, litTable);
						ret = (int)data_comp.getFileSize();
						data_comp.writeToStream(ctx.output);
						data_comp.dispose();
					}
					else {
						FileBuffer data_comp = MyuArcCommon.lzCompressMatch(mf, ctx.lzMode);
						ret = (int)data_comp.getFileSize();
						data_comp.writeToStream(ctx.output);
						data_comp.dispose();
					}
				}
				else {
					FileBuffer data_comp = MyuArcCommon.lzCompress(data);
					ret = (int)data_comp.getFileSize();
					data_comp.writeToStream(ctx.output);
					data_comp.dispose();
				}
			}
			else {
				ret = (int)data.getFileSize();
				data.writeToStream(ctx.output);
			}
			data.dispose();
			ctx.decompSize = ret;
			return ret;
		}
		catch(IOException ex) {
			ex.printStackTrace();
			MyuPackagerLogger.logMessage("MyuUnkTypeHandler.importCallback", 
					"There was an error loading the import file!");
		}
		
		//TODO Sector alignment
		
		
		return 0;
	}

	public boolean exportCallback(ExportContext ctx) {
		//Don't forget to decompress if needed
		
		String filename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
		if(filename == null){
			MyuPackagerLogger.logMessage("MyuUnkTypeHandler.exportCallback", "File name is required for export!");
			return false;
		}
		
		//Prepare output XML node
		String ename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_ENUM);
		if(ename == null){
			//Use file name
			ename = filename.toUpperCase();
			ename = ename.replace(" ", "_");
		}
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, ename);
		ctx.target_out.name = MyupkgConstants.ASSET_TYPE_BIN;
		ctx.target_out.value = ctx.rel_dir + "/" + filename + ".bin";
		
		//Decompress data, if needed.
		boolean decomp_me = false;
		String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_LZCOMP);
		if(aval != null && (!aval.equalsIgnoreCase("false") && !aval.equalsIgnoreCase("none"))) decomp_me = true;
		
		String outpath = ctx.output_dir + File.separator + filename + ".bin";
		if(decomp_me){
			try{
				MatchFile mf = MyuArcCommon.lzDecompressMatch(ctx.data);
				FileBuffer dec = mf.data;
				dec.writeFile(outpath);
				dec.dispose();
				
				if(mf.bufferGarbageString != null && !mf.bufferGarbageString.isEmpty()) {
					ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_GARBAGE, mf.bufferGarbageString);
				}
			}
			catch(IOException ex){
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuUnkTypeHandler.exportCallback", 
						"There was an error writing the output file!");
			}
		}
		else{
			try{
				ctx.data.writeFile(outpath);
			}
			catch(IOException ex){
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuUnkTypeHandler.exportCallback", 
						"There was an error writing the output file!");
			}
		}

		return true;
	}

}
