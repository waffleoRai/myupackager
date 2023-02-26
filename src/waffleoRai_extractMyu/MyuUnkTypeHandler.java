package waffleoRai_extractMyu;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import waffleoRai_Compression.lz77.LZMu;
import waffleoRai_Compression.lz77.LZMu.LZMuDef;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.StreamWrapper;

public class MyuUnkTypeHandler implements TypeHandler{

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
		if(aval != null && (aval.equalsIgnoreCase("true"))) decomp_me = true;
		
		String outpath = ctx.output_dir + File.separator + filename + ".bin";
		if(decomp_me){
			LZMuDef lzdef = LZMu.getDefinition();
			StreamWrapper dec = lzdef.decompress(new FileBufferStreamer(ctx.data));
			try{
				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outpath));
				while(!dec.isEmpty()){
					bos.write(dec.getFull());
				}
				bos.close();
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
