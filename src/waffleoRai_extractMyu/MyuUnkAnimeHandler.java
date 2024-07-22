package waffleoRai_extractMyu;

import java.io.File;
import java.io.IOException;

import waffleoRai_Utils.FileBuffer;

public class MyuUnkAnimeHandler implements TypeHandler{

	@Override
	public boolean exportCallback(ExportContext ctx) {
		String filename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
		if(filename == null){
			MyuPackagerLogger.logMessage("MyuUnkAnimeHandler.exportCallback", "File name is required for export!");
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
		ctx.target_out.name = MyupkgConstants.ASSET_TYPE_ANIMUNK;
		ctx.target_out.value = ctx.rel_dir + "/" + filename + ".bin";
		String outpath = ctx.output_dir + File.separator + filename + ".bin";
		
		//Extract data from sector padding garbage
		//The total data size SEEMS to be the sum of the second and third words
		//This is relative to the file start
		int datasize = ctx.data.intFromFile(4L) + ctx.data.intFromFile(8L);
		try{
			ctx.data.writeFile(outpath, 0, datasize);
		}
		catch(IOException ex){
			ex.printStackTrace();
			MyuPackagerLogger.logMessage("MyuUnkAnimeHandler.exportCallback", 
					"There was an error writing the output file!");
			return false;
		}
		
		return true;
	}

	@Override
	public int importCallback(ImportContext ctx) {
		String filename = ctx.import_specs.value;
		if(filename == null) {
			MyuPackagerLogger.logMessage("MyuUnkAnimeHandler.importCallback", "File name is required for import!");
			return 0;
		}
		
		try {
			int ret = 0;
			FileBuffer data = FileBuffer.createBuffer(filename, false);
			
			ret = (int)data.getFileSize();
			data.writeToStream(ctx.output);
			
			data.dispose();
			ctx.decompSize = ret;
			
			return ret;
		}
		catch(IOException ex) {
			ex.printStackTrace();
			MyuPackagerLogger.logMessage("MyuUnkAnimeHandler.importCallback", 
					"There was an error loading the import file!");
		}
		
		return 0;
	}

}
