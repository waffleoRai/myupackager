package waffleoRai_extractMyu;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import waffleoRai_Image.psx.QXImage;
import waffleoRai_Utils.FileBuffer;

public class MyuImagebankFile {
	
	private QXImage image_bank;
	
	public MyuImagebankFile(){}
	
	public static MyuImagebankFile importBin(FileBuffer data, LiteNode input_specs) throws IOException{
		//Assumes already decompressed.
		//Check if tiled.
		boolean tiled = false;
		if(input_specs.attr.containsKey(MyupkgConstants.XML_ATTR_TILESIZE)) tiled = true;
		
		MyuImagebankFile ibf = new MyuImagebankFile();
		ibf.image_bank = QXImage.readImageData(data, tiled);
		
		return ibf;
	}
	
	public static MyuImagebankFile importImageCollection(LiteNode specs){
		//TODO
		//This imports BMPs and binary CLUTs
		
		return null;
	}
	
	public boolean exportBin(OutputStream out){
		//TODO
		return false;
	}
	
	public boolean exportImageCollection(String dir, LiteNode srcNode, LiteNode trgNode){
		int frame_count = image_bank.getFrameCount();
		int clut_count = image_bank.getPaletteCount();
		
		//Update parent XML node
		//	Need Tiled bool, bit depth, flags(? - when the rest are known)
		boolean is_tiled = false;
		if(srcNode != null){
			is_tiled = srcNode.attr.containsKey(MyupkgConstants.XML_ATTR_TILESIZE);
		}
		if(is_tiled){
			trgNode.attr.put(MyupkgConstants.XML_ATTR_TILESIZE, "32,32");
		}
		trgNode.attr.put(MyupkgConstants.XML_ATTR_BITDEPTH, Integer.toString(image_bank.getBitDepth()));
		
		String[] subfiles = null;
		if(srcNode != null){
			if(!srcNode.children.isEmpty()){
				int i = 0;
				subfiles = new String[srcNode.children.size()];
				for(LiteNode node : srcNode.children){
					if(!node.name.equalsIgnoreCase(MyupkgConstants.XML_NODENAME_SUBFILE)) continue;
					if(node.attr.containsKey(MyupkgConstants.XML_ATTR_FILENAME)){
						subfiles[i] = node.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
					}
					i++;
				}
			}
		}

		//Export CLUTs
		//Create "Clut" Child XML nodes. Just needs value that is bin file path
		for(int i = 0; i < clut_count; i++){
			String outname = String.format("palette_%03d.clut", i);
			String outpath = dir + File.separator + outname;
			//Just put the output file by itself as node path. Dir can be added by calling func
			LiteNode clutnode = new LiteNode();
			trgNode.children.add(clutnode);
			clutnode.parent = trgNode;
			clutnode.name = MyupkgConstants.ASSET_TYPE_CLUT;
			clutnode.value = outname;
			try{image_bank.outputCLUTBinary(outpath, i);}
			catch(Exception ex){
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.exportImageCollection", 
						"CLUT " + i + "export failed!");
				return false;
			}
		}
		
		//Export BMPs
		//Create "Bitmap" Child XML nodes
		//Needs file path (as value), xscale, yscale, clut idx
		for(int i = 0; i < frame_count; i++){
			String outname = String.format("image_%03d.bmp", i);
			if(subfiles != null){
				if(subfiles[i] != null) outname = subfiles[i] + ".bmp";
			}
			String outpath = dir + File.separator + outname;
			try{
				image_bank.writeFrameToBMPFile(outpath, i);
			}
			catch(Exception ex){
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.exportImageCollection", 
						"Bitmap " + i + "export failed!");
				return false;
			}
			
			LiteNode bmpnode = new LiteNode();
			trgNode.children.add(bmpnode);
			bmpnode.parent = trgNode;
			bmpnode.name = MyupkgConstants.ASSET_TYPE_IMAGE;
			bmpnode.value = outname;
			
			int[] scales = image_bank.getFrameScalingFactors(i);
			bmpnode.attr.put(MyupkgConstants.XML_ATTR_XSCALE, Integer.toString(scales[0]));
			bmpnode.attr.put(MyupkgConstants.XML_ATTR_YSCALE, Integer.toString(scales[1]));
			bmpnode.attr.put(MyupkgConstants.XML_ATTR_CLUTIDX, Integer.toString(image_bank.getClutIndexForFrame(i)));
		}
		
		return true;
	}
	
	public static class MyuImagebankHandler implements TypeHandler{
		
		public boolean exportCallback(ExportContext ctx) {
			//I might not bother exporting png since the bmp should make it visible?
			String filename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
			if(filename == null){
				MyuPackagerLogger.logMessage("MyuImagebankFile.MyuImagebankHandler.exportCallback", 
						"File name is required for export!");
				return false;
			}
			
			String ename = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_ENUM);
			if(ename == null){
				//Use file name
				ename = filename.toUpperCase();
				ename = ename.replace(" ", "_");
			}
			
			ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, ename);
			ctx.target_out.name = MyupkgConstants.ASSET_TYPE_IMAGEGROUP;
			String outdir_rel = ctx.rel_dir + "/" + filename;
			String outdir_local = ctx.output_dir + File.separator + filename;
			
			MyuImagebankFile bank = null;
			try{
				boolean lz = false;
				String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_LZCOMP);
				if(aval != null){
					lz = aval.equalsIgnoreCase("true");
				}
				
				FileBuffer idata = ctx.data;
				if(lz){
					idata = MyuArcCommon.lzDecompress(idata);
				}
				
				bank = MyuImagebankFile.importBin(idata, ctx.target_in);
			}
			catch(Exception ex){
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.MyuImagebankHandler.exportCallback", 
						"Failed to import image bank binary \"" + filename + "\"!");
				return false;
			}
			
			try{
				bank.exportImageCollection(outdir_local, ctx.target_in, ctx.target_out);
				//Update paths of child nodes.
				for(LiteNode child : ctx.target_out.children){
					child.value = outdir_rel + "/" + child.value;
				}
			}
			catch(Exception ex){
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.MyuImagebankHandler.exportCallback", 
						"Failed to export image bank \"" + filename + "\"!");
				return false;
			}
			
			return true;
		}
		
	}

}
