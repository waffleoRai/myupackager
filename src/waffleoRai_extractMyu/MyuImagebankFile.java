package waffleoRai_extractMyu;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

import waffleoRai_Image.psx.QXImage;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.MultiFileBuffer;

public class MyuImagebankFile {
	
	private boolean tiled;
	private QXImage image_bank;
	private FileBuffer rawBinData;
	
	public MyuImagebankFile(){}
	
	public int calcBinHeaderSize() {
		if(image_bank == null) return 0;
		return 4 + (image_bank.getFrameCount() << 4);
	}
	
	public int calcBinPaletteBlockSize() {
		if(image_bank == null) return 0;
		int psize = 0;
		switch(image_bank.getBitDepth()) {
		case 4: psize = 16; break;
		case 8: psize = 256; break;
		default: return 0;
		}
		return 4 + (image_bank.getPaletteCount() * (psize << 1));
	}
	
	public static MyuImagebankFile importBin(FileBuffer data, LiteNode input_specs) throws IOException{
		//Assumes already decompressed.
		//Check if tiled.
		boolean tiled = false;
		if(input_specs.attr.containsKey(MyupkgConstants.XML_ATTR_TILESIZE)) tiled = true;
		
		MyuImagebankFile ibf = new MyuImagebankFile();
		ibf.image_bank = QXImage.readImageData(data, tiled);
		ibf.rawBinData = data;
		
		return ibf;
	}
	
	private static int parseTextCLUTEntry(String value) {
		//Returns ARGB
		if(value.contains(",")) {
			//Assumed decimal
			int out = 0;
			String[] spl = value.split(",");
			for(int i = 0; i < spl.length; i++) {
				out <<= 8;
				out |= Integer.parseInt(spl[i]) & 0xff;
			}
			return out;
		}
		else {
			//Assumed hex
			value = value.replace("#", "");
			value = value.replace("0x", "");
			return Integer.parseUnsignedInt(value, 16);
		}
	}
	
	private static boolean importBitmap(LiteNode bmpNode, QXImage qx, int i) {
		String filepath = bmpNode.value;
		int clutidx = -1;
		int clutImport = QXImage.CLUT_IMPORT_OP_NONE;
		int xfac = 0;
		int yfac = 0;
		int unk05 = 0;
		int unk06 = 0;
		int unk07 = 0;
		
		if(filepath == null) {
			MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
					"Path to image file is required for image element!");
			return false;
		}
		
		//Handle attributes...
		String sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_CLUTIDX);
		if(sval != null) {
			try {clutidx = Integer.parseInt(sval);}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"CLUT index is invalid!");
				return false;
			}
		}
		
		sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_XSCALE);
		if(sval != null) {
			try {xfac = Integer.parseInt(sval);}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"X image factor must be a valid integer.");
				return false;
			}
		}
		
		sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_YSCALE);
		if(sval != null) {
			try {yfac = Integer.parseInt(sval);}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"Y image factor must be a valid integer.");
				return false;
			}
		}
		
		sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_UNK05);
		if(sval != null) {
			try {unk05 = Integer.parseInt(sval);}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"Unk05 must be a valid integer.");
				return false;
			}
		}
		
		sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_UNK06);
		if(sval != null) {
			try {unk06 = Integer.parseInt(sval);}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"Unk06 must be a valid integer.");
				return false;
			}
		}
		
		sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_UNK07);
		if(sval != null) {
			try {unk07 = Integer.parseInt(sval);}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"Unk07 must be a valid integer.");
				return false;
			}
		}
		
		sval = bmpNode.attr.get(MyupkgConstants.XML_ATTR_CLUT_IMPORT);
		if(sval != null) {
			if(sval.equals(MyupkgConstants.CLUT_IMPORT_TYPE_GENERATE)) {
				clutImport = QXImage.CLUT_IMPORT_OP_TRY_CLUT_GEN;
			}
			else if(sval.equals(MyupkgConstants.CLUT_IMPORT_TYPE_MATCH)) {
				clutImport = QXImage.CLUT_IMPORT_OP_TRY_CLUT_MATCH;
			}
			else if(sval.equals(MyupkgConstants.CLUT_IMPORT_TYPE_SOURCEFILE)) {
				clutImport = QXImage.CLUT_IMPORT_OP_IMGFILE_CLUT;
			}
			else {
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"CLUT import behavior \"" + sval + "\" is invalid!");
				return false;
			}
		}
		
		if(filepath.endsWith(".bmp")) {
			try {
				FileBuffer bmpdata = FileBuffer.createBuffer(filepath, false);
				qx.importFrameFromBMP(bmpdata, i, clutidx, clutImport);
			}
			catch(IOException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"BMP file \"" + filepath + "\" could not be found!");
				return false;
			} 
			catch (UnsupportedFileTypeException e) {
				e.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"BMP file \"" + filepath + "\" could not be read!");
				return false;
			}
		}
		else if(filepath.endsWith(".png")) {
			try {
				BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filepath));
				qx.importFrameFromFile_ARGB(bis, i, clutidx, clutImport);
				bis.close();
			}
			catch(IOException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
						"Image file \"" + filepath + "\" could not be read!");
				return false;
			} 
		}
		else {
			MyuPackagerLogger.logMessage("MyuImagebankFile.importBitmap", 
					"Input image type for \"" + filepath + "\" not recognized.");
			return false;
		}
		
		qx.setBitmapXYFactors(xfac, yfac, i);
		qx.setFrameUnk05(i, unk05);
		qx.setFrameUnk06(i, unk06);
		qx.setFrameUnk07(i, unk07);
		
		return true;
	}
	
	public static MyuImagebankFile importImageCollection(LiteNode specs){
		//This imports BMPs and binary CLUTs
		//Can also take anything Java ImageIO can read (like PNG). However, proper palette mapping is not guaranteed.
		
		if(specs == null) return null;
		
		int bitdepth = 8; //Default
		boolean isTiled = false;
		int clutCount = 0;
		int bmpCount = 0;
		String workDir = specs.value;
		if(workDir == null) {
			MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
					"Importer was provided no working directory!");
			return null;
		}
		
		if(specs.attr.containsKey(MyupkgConstants.XML_ATTR_TILESIZE)) isTiled = true;
		if(specs.attr.containsKey(MyupkgConstants.XML_ATTR_BITDEPTH)) {
			try {
				bitdepth = Integer.parseInt(specs.attr.get(MyupkgConstants.XML_ATTR_BITDEPTH));
			}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
						"Specified bit depth \"" + specs.attr.get(MyupkgConstants.XML_ATTR_BITDEPTH) + "\" is not valid.");
				return null;
			}
		}
		
		//Unk flags
		int miscFlags = 0x5841;
		if(specs.attr.containsKey(MyupkgConstants.XML_ATTR_UNK_FLAGS)) {
			try {
				miscFlags = Integer.parseInt(specs.attr.get(MyupkgConstants.XML_ATTR_UNK_FLAGS), 16);
			}
			catch(NumberFormatException ex) {
				ex.printStackTrace();
				MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
						"Specified unk flags \"" + specs.attr.get(MyupkgConstants.XML_ATTR_UNK_FLAGS) + "\" is not valid.");
				return null;
			}
		}
		
		//Count (specified) CLUTs and bitmaps
		for(LiteNode child : specs.children) {
			if(child.name.equals(MyupkgConstants.ASSET_TYPE_CLUT)) clutCount++;
			else if(child.name.equals(MyupkgConstants.ASSET_TYPE_IMAGE)) bmpCount++;
		}
		
		QXImage qx = new QXImage(bmpCount, bitdepth);
		qx.allocCLUTs(clutCount);
		qx.setUnkFlagField(miscFlags);
		
		//Import known CLUTs
		int i = 0;
		for(LiteNode child : specs.children) {
			if(child.name.equals(MyupkgConstants.ASSET_TYPE_CLUT)) {
				if(child.value != null) {
					//Pull from file
					String filepath = child.value;
					filepath = MyuArcCommon.unixRelPath2Local(workDir, filepath);
					if(filepath.endsWith(".clut")) {
						//Raw CLUT file
						try {
							FileBuffer clutdat = FileBuffer.createBuffer(filepath, false);
							short[] clut = new short[1 << bitdepth];
							clutdat.setCurrentPosition(0L);
							for(int j = 0; j < clut.length; j++) clut[j] = clutdat.nextShort();
							qx.loadCLUT(clut, i);
						}
						catch(IOException ex) {
							ex.printStackTrace();
							MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
									"Specified CLUT file \"" + filepath + "\" could not be found!");
						}
					}
					else {
						//Check for format specifications in the attributes
						if(child.attr.containsKey(MyupkgConstants.XML_ATTR_CLUT_FORMAT)) {
							String fmtstr = child.attr.get(MyupkgConstants.XML_ATTR_CLUT_FORMAT);
							boolean bigEndian = false;
							if(child.attr.containsKey(MyupkgConstants.XML_ATTR_BYTE_ORDER)) {
								String estr = child.attr.get(MyupkgConstants.XML_ATTR_BYTE_ORDER);
								if(estr.equals(MyupkgConstants.BYTE_ORDER_BIG_ENDIAN)) bigEndian = true;
							}
							
							boolean okay = false;
							int[] clut = new int[1 << bitdepth];
							if(fmtstr.equals(MyupkgConstants.CLUT_FORMAT_ARGB)) {
								try {
									FileBuffer clutdat = FileBuffer.createBuffer(filepath, bigEndian);
									for(int j = 0; j < clut.length; j++) clut[j] = clutdat.nextInt();
									okay = true;
								}
								catch(IOException ex) {
									MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
											"Specified CLUT file \"" + filepath + "\" could not be found!");
									ex.printStackTrace();
								}
							}
							else if(fmtstr.equals(MyupkgConstants.CLUT_FORMAT_RGB)) {
								try {
									FileBuffer clutdat = FileBuffer.createBuffer(filepath, bigEndian);
									for(int j = 0; j < clut.length; j++) {
										clut[j] = clutdat.nextShortish() | 0xff000000;
									}
									okay = true;
								}
								catch(IOException ex) {
									MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
											"Specified CLUT file \"" + filepath + "\" could not be found!");
									ex.printStackTrace();
								}
							}
							else if(fmtstr.equals(MyupkgConstants.CLUT_FORMAT_TEXT_DEC) ||
									fmtstr.equals(MyupkgConstants.CLUT_FORMAT_TEXT_HEX)) {
								try {
									BufferedReader br = new BufferedReader(new FileReader(filepath));
									int j = 0;
									String line = null;
									while((line = br.readLine()) != null) {
										if(j >= clut.length) break;
										try {
											clut[j++] = parseTextCLUTEntry(line);
										}
										catch(Exception ex) {
											ex.printStackTrace();
											MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
													"Invalid CLUT entry!");
											break;
										}
									}
									br.close();
									okay = (j == clut.length);
								}
								catch(IOException ex) {
									MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
											"Specified CLUT file \"" + filepath + "\" could not be found!");
									ex.printStackTrace();
								}
							}
							else {
								MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
										"CLUT format \"" + fmtstr + "\" not recognized!");
							}
							
							if(okay) qx.loadCLUT(clut, i);
						}
						else {
							MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
									"Specified CLUT file \"" + filepath + "\" format needs to be specified!");
						}
					}
				}
				else {
					//specified in xml
					int[] clut = new int[1 << bitdepth];
					int j = 0;
					for(LiteNode grandchild : child.children) {
						if(child.name.equals(MyupkgConstants.ASSET_TYPE_CLUTENTRY)) {
							//Either a hexcode (ARGB or RGB) or comma sep decimal trio or quartet
							try {
								clut[j++] = parseTextCLUTEntry(grandchild.value);
							}
							catch(Exception ex) {
								ex.printStackTrace();
								MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
										"Invalid CLUT entry!");
							}
						}
					}
					
					//Add to qx
					qx.loadCLUT(clut, i);
				}
				i++;
			}
		}
		
		//Import bitmaps
		i = 0;
		for(LiteNode child : specs.children) {
			String filepath = child.value;
			if(filepath == null) {
				MyuPackagerLogger.logMessage("MyuImagebankFile.importImageCollection", 
						"Filepath was not provided for bitmap " + i + "!");
				continue;
			}
			if(child.name.equals(MyupkgConstants.ASSET_TYPE_IMAGE)) {
				child.value = MyuArcCommon.unixRelPath2Local(workDir, filepath);
				if (importBitmap(child, qx, i)) i++;
			}
		}
		
		MyuImagebankFile ib = new MyuImagebankFile();
		ib.image_bank = qx;
		ib.tiled = isTiled;
		return ib;
	}
	
	public int exportBin(ImportContext ctx){
		if(image_bank == null) return 0;
		try {
			//Do bitmap specific buffer garbage
			if(ctx.matchFlag) {
				int i = 0;
				for(LiteNode child : ctx.import_specs.children) {
					if(child.name.equals(MyupkgConstants.ASSET_TYPE_IMAGE)) {
						String garbage = child.attr.get(MyupkgConstants.XML_ATTR_GARBAGE);
						if(garbage != null) {
							FileBuffer gdat = MyuArcCommon.bufferGarbageString2Data(garbage);
							image_bank.setFrameMatchGarbage(i, gdat.getBytes(0, gdat.getFileSize()));
							gdat.dispose();
						}
						i++;
					}
				}
			}
			
			FileBuffer buff = image_bank.serializeMe(tiled);
			
			//Check for buffer garbage *included* in dec'd file
			if(ctx.matchFlag) {
				String bufferJunk = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_OVRFL);
				if(bufferJunk != null) {
					FileBuffer junkdata = null;
					if(bufferJunk.contains("/")) {
						//It's a file path.
						String path = MyuArcCommon.unixRelPath2Local(ctx.wd, bufferJunk);
						if(FileBuffer.fileExists(path)) {
							junkdata = FileBuffer.createBuffer(path);
						}
						else {
							MyuPackagerLogger.logMessage("MyuImagebankFile.exportBin", 
									"Could not open buffer junk file: " + path);
						}
					}
					else {
						//Data literal
						junkdata = MyuArcCommon.bufferGarbageString2Data(bufferJunk);
					}
					FileBuffer multi = new MultiFileBuffer(2);
					multi.addToFile(buff);
					multi.addToFile(junkdata);
					buff = multi;
				}
			}
			ctx.decompSize = (int)buff.getFileSize();

			if(ctx.lzMode > 0) {
				if(ctx.matchFlag) {
					String bufferJunk = ctx.import_specs.attr.get(MyupkgConstants.XML_ATTR_GARBAGE);
					MatchFile mf = new MatchFile();
					mf.data = buff;
					mf.bufferGarbageString = bufferJunk;
					buff = MyuArcCommon.lzCompressMatch(mf, ctx.lzMode);
				}
				else {
					buff = MyuArcCommon.lzCompress(buff);
				}
			}
			buff.writeToStream(ctx.output);
			int sz = (int)buff.getFileSize();
			buff.dispose();
			return sz;
		}
		catch(IOException ex) {
			ex.printStackTrace();
			MyuPackagerLogger.logMessage("MyuImagebankFile.exportBin", 
					"Error writing to output stream!");
		}
		
		return 0;
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
		
		//Note mystery flags
		int miscFlags = image_bank.getUnkFlagField();
		miscFlags &= ~QXImage.HDR_FLAG_4BIT;
		trgNode.attr.put(MyupkgConstants.XML_ATTR_UNK_FLAGS, String.format("%04x", miscFlags));
		
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
		long binPos = calcBinHeaderSize()  + calcBinPaletteBlockSize();
		int bd = image_bank.getBitDepth();
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
			
			int unkval = image_bank.getFrameUnk05(i);
			if(unkval != 0) bmpnode.attr.put(MyupkgConstants.XML_ATTR_UNK05, Integer.toString(unkval));
			unkval = image_bank.getFrameUnk06(i);
			if(unkval != 0) bmpnode.attr.put(MyupkgConstants.XML_ATTR_UNK06, Integer.toString(unkval));
			unkval = image_bank.getFrameUnk07(i);
			if(unkval != 0) bmpnode.attr.put(MyupkgConstants.XML_ATTR_UNK07, Integer.toString(unkval));
			
			//Scan for buffer garbage IN BETWEEN bitmaps because apparently we have to do that
			int iw = image_bank.getFrameWidth(i);
			int ih = image_bank.getFrameHeight(i);
			int bitmapSize = 0;
			switch(bd) {
			case 4:
				bitmapSize = ((iw + 1) & ~0x1) * ih;
				bitmapSize >>>= 1;
				break;
			case 8:
				bitmapSize = ih * iw;
				break;
			case 16:
				bitmapSize = ih * iw;
				bitmapSize <<= 1;
				break;
			}
			
			binPos += bitmapSize;
			if((bitmapSize & 0x3) != 0) {
				//Will need padding to next bitmap. Check for any nonzero data.
				int padAmt = 4 - (bitmapSize & 0x3);
				boolean hasGarbage = false;
				for(int j = 0; j < padAmt; j++) {
					if(rawBinData.getByte(binPos + j) != 0) {
						hasGarbage = true;
						break;
					}
				}
				if(hasGarbage) {
					byte[] garbage = new byte[padAmt];
					for(int j = 0; j < padAmt; j++) {
						garbage[j] = rawBinData.getByte(binPos + j);
					}
					String garbageStr = MyuArcCommon.bufferGarbageData2String(garbage);
					bmpnode.attr.put(MyupkgConstants.XML_ATTR_GARBAGE, garbageStr);
				}
				binPos += padAmt;
			}
		}
		
		return true;
	}
	
	public static class MyuImagebankHandler implements TypeHandler{
		
		public int importCallback(ImportContext ctx){
			if(ctx == null) return 0;
			if(ctx.import_specs == null) return 0;
			if(ctx.output == null) return 0;
			//TODO outpos is for sector alignment
			
			MyuImagebankFile ib = MyuImagebankFile.importImageCollection(ctx.import_specs);
			if(ib == null) {
				MyuPackagerLogger.logMessage("MyuImagebankFile.MyuImagebankHandler.importCallback", 
						"Import failed!");
				return 0;
			}
			
			int csize = ib.exportBin(ctx);
			
			return csize;
		}
		
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
			
			//Check for buffer garbage (included in dec file)
			String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_OVRFL);
			if(aval != null) {
				if(aval.contains("/")) {
					//Update path to be relative to output instead.
					String abspath = MyuArcCommon.unixRelPath2Local(ctx.arcspec_wd, aval);
					aval = MyuArcCommon.localPath2UnixRel(ctx.xml_wd, abspath);
				}
				ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_OVRFL, aval);
			}
			
			ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, ename);
			ctx.target_out.name = MyupkgConstants.ASSET_TYPE_IMAGEGROUP;
			String outdir_rel = ctx.rel_dir + "/" + filename;
			String outdir_local = ctx.output_dir + File.separator + filename;
			
			MyuImagebankFile bank = null;
			try{
				boolean lz = false;
				aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_LZCOMP);
				if(aval != null){
					lz = !aval.equalsIgnoreCase("none");
				}
				
				FileBuffer idata = ctx.data;
				if(lz){
					MatchFile mf = MyuArcCommon.lzDecompressMatch(idata);
					if(mf.bufferGarbageString != null && !mf.bufferGarbageString.isEmpty()) {
						ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_GARBAGE, mf.bufferGarbageString);
					}
					idata = mf.data;
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
