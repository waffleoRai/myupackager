package waffleoRai_extractMyu;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import waffleoRai_Containers.ISO;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Sound.psx.PSXXAAudio;
import waffleoRai_Sound.psx.PSXXAStream;
import waffleoRai_Sound.psx.XAAudioDataOnlyStream;
import waffleoRai_Sound.psx.XAAudioStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.StringUtils;

public class MdecMovieHandler {
	
	public String wd;
	public LiteNode import_spec;
	
	public boolean doubleSpeed = true;
	
	public String[] eNames;
	public int[] infoTable;
	public int count = 0;
	public int endSector = 0;
	
	public byte[] writeBuffer;
	public boolean wbused = false;
	public BufferedOutputStream outStream;
	public List<VideoChannelInfo> vChannels;
	public List<AudioChannelInfo> aChannels;
	
	private static class VideoChannelInfo{
		public int channel = 1;
		public String path;
		
		public InputStream input;
		public boolean eof = false;
		private boolean eofFound = false;
		//public int lastSec = -1;
		
		private byte[] nextSec;
		
		public void open() throws IOException {
			input = new BufferedInputStream(new FileInputStream(path));
			bufferNextSector();
		}
		
		public void bufferNextSector() throws IOException {
			if(eof) return;
			if(eofFound) {
				eof = true;
				nextSec = null; 
				return;
			}
			
			if(nextSec == null) nextSec = new byte[0x800];
			
			int b = input.read();
			if(b == -1) {
				eof = true;
				nextSec = null;
				return;
			}
			nextSec[0] = (byte)b;
			
			for(int i = 1; i < 0x800; i++) {
				if(!eofFound) {
					b = input.read();
					if(b != -1) {
						nextSec[i] = (byte)b;
					}
					else {
						eofFound = true;
						nextSec[i] = (byte)0;
					}
				}
				else nextSec[i] = (byte)0;
			}
		}
	
		public void copyNextSectorTo(byte[] target, int offset) throws IOException {
			if(eof) return;
			for(int i = 0; i < nextSec.length; i++) target[offset++] = nextSec[i];
			bufferNextSector();
		}
	}
	
	private static class AudioChannelInfo{
		public boolean srHalf = false;
		public boolean bdDouble = false;
		public boolean isStereo = false;
		public int channel = 1;
		public byte subByte4 = 0;
		
		public String path;
		public XAAudioDataOnlyStream data;
		private byte[] nextSec;
		public boolean eof = false;
		
		public int ileaveDenom;
		public int lastSec = -1;
		
		public void open() throws IOException, UnsupportedFileTypeException {
			FileBuffer buff = FileBuffer.createBuffer(path, true);
			data = PSXXAAudio.readAifc(buff.getReferenceAt(0L));
			buff.dispose();
			bufferNextSector();
		}
		
		public void bufferNextSector() {
			if(eof) return;
			nextSec = data.nextSectorBytes();
			if(nextSec == null) {
				eof = true;
			}
		}
	
		public void copyNextSectorTo(byte[] target, int offset) throws IOException {
			if(eof) return;
			for(int i = 0; i < nextSec.length; i++) target[offset++] = nextSec[i];
			bufferNextSector();
		}
	}
	
	private void flushWriteBuffer() throws IOException {
		if(wbused) outStream.write(writeBuffer); //FLUSH
		else wbused = true;
	}
	
	private void readChSpecs(LiteNode movieNode) throws IOException, UnsupportedFileTypeException {
		vChannels.clear();
		aChannels.clear();
		
		String aval = null;
		for(LiteNode gchild : movieNode.children) {
			if(gchild.name == null) continue;
			if(gchild.name.equals(MyupkgConstants.ASSET_TYPE_XAVIDEO)) {
				VideoChannelInfo vinfo = new VideoChannelInfo();
				vinfo.path = MyuArcCommon.unixRelPath2Local(wd, gchild.value);
				vinfo.open();
				
				aval = gchild.attr.get(MyupkgConstants.XML_ATTR_STRCHANNEL);
				if(aval != null) vinfo.channel = MyuArcCommon.parseInt(aval);
				
				vChannels.add(vinfo);
			}
			else if(gchild.name.equals(MyupkgConstants.ASSET_TYPE_XAAUDIO)) {
				AudioChannelInfo ainfo = new AudioChannelInfo();
				ainfo.path = MyuArcCommon.unixRelPath2Local(wd, gchild.value);
				
				aval = gchild.attr.get(MyupkgConstants.XML_ATTR_STRCHANNEL);
				if(aval != null) ainfo.channel = MyuArcCommon.parseInt(aval);
				
				aval = gchild.attr.get(MyupkgConstants.XML_ATTR_SAMPLERATE);
				if(aval != null) {
					int temp = MyuArcCommon.parseInt(aval);
					if(temp != 37800) ainfo.srHalf = true;
				}
				
				aval = gchild.attr.get(MyupkgConstants.XML_ATTR_BITDEPTH);
				if(aval != null) {
					int temp = MyuArcCommon.parseInt(aval);
					if(temp != 4) ainfo.bdDouble = true;
				}
				
				aval = gchild.attr.get(MyupkgConstants.XML_ATTR_IS_STEREO);
				if(aval != null && aval.equalsIgnoreCase("true")) ainfo.isStereo = true;
				
				//Calculate interleave.
				ainfo.ileaveDenom = 8;
				if(!ainfo.isStereo) ainfo.ileaveDenom <<= 1;
				if(ainfo.srHalf) ainfo.ileaveDenom <<= 1;
				
				int b4 = 0;
				if(ainfo.isStereo) b4 |= (1 << 0);
				if(ainfo.srHalf) b4 |= (1 << 2);
				if(ainfo.bdDouble) b4 |= (1 << 4);
				ainfo.subByte4 = (byte)b4;
				
				//Load audio data
				ainfo.open();
				
				aChannels.add(ainfo);
			}
			else {
				MyuPackagerLogger.logMessage("MdecMovieHandler.readChSpecs", 
						"WARNING: Channel type \"" + gchild.name + "\" not recognized. Skipping...");
			}
		}
		
	}
	
	private int writeVideoChannel(VideoChannelInfo vinfo, int xaFile) throws IOException {
		int s = 0;
		
		boolean audioRemaining = !aChannels.isEmpty();
		while(!vinfo.eof) {
			//Do 7 sectors of video, then 1 of audio.
			//If audio isn't in matching channel or same speed, results will be strange.
			//So make sure your xml is right.
			
			int vSecWritten = 0;
			for(int j = 0; j < 7; j++) {
				if(vinfo.eof) break;
				
				flushWriteBuffer();
				writeBuffer[0x10] = (byte)(xaFile+1);
				writeBuffer[0x11] = (byte)(vinfo.channel);
				writeBuffer[0x12] = (byte)(0x48);
				writeBuffer[0x13] = (byte)(0);
				for(int k = 0; k < 4; k++) writeBuffer[0x14 + k] = writeBuffer[0x10 + k];
				for(int k = 0x818; k < ISO.SECSIZE; k++) writeBuffer[k] = 0;
				
				vinfo.copyNextSectorTo(writeBuffer, 0x18);
				
				vSecWritten++; s++;
			}
			
			if(audioRemaining) {
				//Pad.
				while(vSecWritten < 7) {
					
					flushWriteBuffer();
					writeBuffer[0x11] = (byte)(0);
					writeBuffer[0x12] = (byte)(0);
					writeBuffer[0x13] = (byte)(0);
					for(int k = 0; k < 4; k++) writeBuffer[0x14 + k] = writeBuffer[0x10 + k];
					for(int k = 0x18; k < ISO.SECSIZE; k++) writeBuffer[k] = 0;
					
					vSecWritten++; s++;
				}
			}
			
			//Write 1 audio (if applicable).
			audioRemaining = false;
			for(AudioChannelInfo ainfo : aChannels) {
				if(ainfo.eof) continue;
				if((ainfo.lastSec == -1) || (s == (ainfo.lastSec + ainfo.ileaveDenom))) {
					flushWriteBuffer();
					writeBuffer[0x10] = (byte)(xaFile+1);
					writeBuffer[0x11] = (byte)(ainfo.channel);
					writeBuffer[0x12] = (byte)(0x64);
					writeBuffer[0x13] = ainfo.subByte4;
					for(int k = 0; k < 4; k++) writeBuffer[0x14 + k] = writeBuffer[0x10 + k];
					for(int k = 0x18; k < ISO.SECSIZE; k++) writeBuffer[k] = 0;
					
					ainfo.copyNextSectorTo(writeBuffer, 0x18);
					
					audioRemaining = !ainfo.eof;
					ainfo.lastSec = s++;
					break;
				}
			}
		}
		
		return s;
	}
	
	private int writeRemainingAudio(int xaFile) throws IOException {
		int s = 0;
		boolean adone = false;
		while(!adone) {
			adone = true;
			for(AudioChannelInfo ainfo : aChannels) {
				if(!ainfo.eof) {
					adone = false;
					
					if((ainfo.lastSec == -1) || (s == (ainfo.lastSec + ainfo.ileaveDenom))) {
						flushWriteBuffer();
						writeBuffer[0x10] = (byte)(xaFile+1);
						writeBuffer[0x11] = (byte)(ainfo.channel);
						writeBuffer[0x12] = (byte)(0x64);
						writeBuffer[0x13] = ainfo.subByte4;
						for(int k = 0; k < 4; k++) writeBuffer[0x14 + k] = writeBuffer[0x10 + k];
						for(int k = 0x18; k < ISO.SECSIZE; k++) writeBuffer[k] = 0;
						
						ainfo.copyNextSectorTo(writeBuffer, 0x18);

						ainfo.lastSec = s++;
					}
					else {
						MyuPackagerLogger.logMessage("MdecMovieHandler.writeRemainingAudio", 
								"WARNING: Audio channel " + ainfo.channel + " interleave failed. Channel will cut out early.");
						ainfo.eof = true;
					}
				}
			}
		}
		return s;
	}
	
	public void buildStream(String outpath) throws IOException, UnsupportedFileTypeException {
		int i = 0;
		int s = 0;
		int slotAlloc = import_spec.children.size();

		eNames = new String[slotAlloc];
		infoTable = new int[slotAlloc];
		
		String aval = import_spec.attr.get(MyupkgConstants.XML_ATTR_DBLSPEED);
		if(aval != null && aval.equalsIgnoreCase("true")) doubleSpeed = true;
		else doubleSpeed = false;
		
		outStream = new BufferedOutputStream(new FileOutputStream(outpath));
		writeBuffer = MyuCD.genDummySecBaseI_M2F1();
		wbused = false;
		
		vChannels = new ArrayList<VideoChannelInfo>(8);
		aChannels = new ArrayList<AudioChannelInfo>(8);
		for(LiteNode child : import_spec.children) {
			if(child.name == null) continue;
			if(child.name.equals(MyupkgConstants.ASSET_TYPE_XAMOVIE)) {
				int xaFile = 0;
				int settings = 0;
				String eName = child.attr.get(MyupkgConstants.XML_ATTR_ENUM);
				
				if(eName == null) eName = String.format("FMV_%02x", i);
				eNames[i] = eName;
				
				aval = child.attr.get(MyupkgConstants.XML_ATTR_XAFILEIDX);
				if(aval != null) xaFile = MyuArcCommon.parseInt(aval);
				aval = child.attr.get(MyupkgConstants.XML_ATTR_SETTINGS);
				if(aval != null) settings = MyuArcCommon.parseInt(aval);
				infoTable[i] = s | (settings << 24);
				
				readChSpecs(child);
				
				MyuPackagerLogger.logMessage("MdecMovieHandler.buildStream", 
						"Now merging in " + eName + "...");
				
				//Write
				for(VideoChannelInfo vinfo : vChannels) {
					s += writeVideoChannel(vinfo, xaFile);
				}
				
				//Anything left of audio?
				s += writeRemainingAudio(xaFile);
				
				//Close video streams.
				for(VideoChannelInfo vinfo : vChannels) {
					vinfo.input.close();
				}

				i++;
				count++;
			}
			else {
				MyuPackagerLogger.logMessage("MdecMovieHandler.buildStream", 
						"WARNING: Cannot handle top level stream type for movie arc besides MdecMovie!");
			}
		}
		
		//Note: don't forget EOF bit on last sector...
		writeBuffer[0x12] = (byte)(Byte.toUnsignedInt(writeBuffer[0x12]) | 0x80);
		writeBuffer[0x16] = writeBuffer[0x12];
		flushWriteBuffer();
		wbused = false;
		endSector = s;
		
		outStream.close();
	}
	
	public void exportH(String path) throws IOException {
		String arcName = import_spec.attr.get(MyupkgConstants.XML_ATTR_NAME);
		if(arcName == null) arcName = "MOVIE";
		arcName = arcName.toLowerCase();
		arcName = StringUtils.capitalize(arcName);
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		bw.write("#ifndef RESARC_" + arcName.toUpperCase() + "_H\n");
		bw.write("#define RESARC_" + arcName.toUpperCase() + "_H\n\n");
		
		bw.write("/* ---------------------------------------------\n");
		bw.write("*  Autogenerated by MyuPackager arcpack\n");
		bw.write("*  ---------------------------------------------*/\n\n");
		
		bw.write("#include \"psx/PSXTypes.h\"\n\n");
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("extern \"C\" {\n");
		bw.write("#endif\n\n");
		
		boolean lastNull = true;
		bw.write("typedef enum {\n");
		for(int i = 0; i < count; i++) {
			if(eNames[i] != null) {
				bw.write(String.format("\t/*%02d*/\t", i));
				bw.write(eNames[i]);
				if(lastNull) bw.write(" = " + i);
				bw.write(",\n");
				lastNull = false;
			}
			else lastNull = true;
		}
		bw.write("} " + arcName + "ResFile;\n\n");
		
		String ctName = import_spec.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_CLIP);
		if(ctName == null) ctName = "gMovieInfo";
		
		bw.write("\textern uint32_t " + ctName + "[];\n\n");
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("}\n");
		bw.write("#endif\n\n");
		
		bw.write("#endif\n");
		bw.close();
	}
	
	public void exportC(String path, String hRel) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		bw.write("/* ---------------------------------------------\n");
		bw.write("*  Autogenerated by MyuPackager arcpack\n");
		bw.write("*  ---------------------------------------------*/\n\n");
		
		bw.write("#include \"Movie.h\"\n\n");
		bw.write("#include \"" + hRel + "\"\n\n");
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("extern \"C\" {\n");
		bw.write("#endif\n\n");
		
		//Info table
		String ctName = import_spec.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_CLIP);
		if(ctName == null) ctName = "gMovieInfo";
		bw.write("\tuint32_t " + ctName + "[] = {");
		for(int i = 0; i < count; i++) {
			if((i & 0x7) == 0) {
				bw.write("\n\t\t");
			}
			if(eNames[i] != null) {
				bw.write(String.format("0x%08x, ", infoTable[i]));
			}
			else {
				bw.write(String.format("0x%08x, ", 0));
			}
		}
		bw.write(String.format("0x%08x", endSector));
		bw.write("\n\t};\n\n");
		
		//Additional imports, if present.
		String cmergePath = import_spec.attr.get(MyupkgConstants.XML_ATTR_CMERGE);
		if(cmergePath != null) {
			cmergePath = MyuArcCommon.unixRelPath2Local(wd, cmergePath);
			BufferedReader br = new BufferedReader(new FileReader(cmergePath));
			String line = null;
			while((line = br.readLine()) != null) bw.write(line + "\n");
			br.close();
		}
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("}\n");
		bw.write("#endif\n\n");
		
		bw.close();
	}

	public static boolean export(ExportContext ctx) throws IOException, UnsupportedFileTypeException {
		String fileName = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
		String enumName = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_ENUM);
		
		int vCh = -1;
		int aCh = -1;
		int aSampleRate = -1;
		int aBitDepth = -1;
		int aChCount = -1;
		
		int startSec = 0;
		int sizeSecs = 0;
		int xaFile = 0;
		int setting = 0;
		
		String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_XAFILEIDX);
		if(aval != null) xaFile = MyuArcCommon.parseInt(aval);
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_SETTINGS);
		if(aval != null) setting = MyuArcCommon.parseInt(aval);
		
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_SECCOUNT);
		if(aval != null) sizeSecs = MyuArcCommon.parseInt(aval);
		else {
			MyuPackagerLogger.logMessage("MdecMovieHandler.export", 
					"Sector count required! Otherwise cannot find clip.");
			return false;
		}
		
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_START);
		if(aval != null) startSec = MyuArcCommon.parseInt(aval);
		else {
			MyuPackagerLogger.logMessage("MdecMovieHandler.export", 
					"Start sector required! Otherwise cannot find clip.");
			return false;
		}
		
		if(enumName == null) {
			enumName = "MovieClip_" + xaFile + "_" + startSec;
		}
		if(fileName == null) {
			fileName = enumName.toUpperCase();
		}
		
		MyuPackagerLogger.logMessage("MdecMovieHandler.export", 
				"Working on movie " + fileName + "...");
		
		//ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_FILENAME, fileName);
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, enumName);
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_XAFILEIDX, Integer.toString(xaFile));
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_SETTINGS, Integer.toString(setting));
		
		LiteNode vidNode = ctx.target_out.newChild(MyupkgConstants.ASSET_TYPE_XAVIDEO);
		LiteNode audNode = ctx.target_out.newChild(MyupkgConstants.ASSET_TYPE_XAAUDIO);
		String movieDir = ctx.output_dir + File.separator + fileName;
		if(!FileBuffer.directoryExists(movieDir)) Files.createDirectories(Paths.get(movieDir));
		String vidPath = movieDir + File.separator + fileName + ".mdec";
		String audPath = movieDir + File.separator + fileName + ".aifc";
		vidNode.value = MyuArcCommon.localPath2UnixRel(ctx.xml_wd, vidPath);
		audNode.value = MyuArcCommon.localPath2UnixRel(ctx.xml_wd, audPath);
		
		XAStreamFile strf = ctx.xaStr.getFile(xaFile);
		FileNode strfSrc = strf.getAsFileNode("str");
		FileBuffer strDat = strfSrc.loadData();

		//Stream video and audio tracks to files. Then read audio back in and convert to aifc.
		String audPathTemp = movieDir + File.separator + fileName + ".str";
		BufferedOutputStream vidOut = new BufferedOutputStream(new FileOutputStream(vidPath));
		BufferedOutputStream audOut = new BufferedOutputStream(new FileOutputStream(audPathTemp));
		long cpos = startSec * ISO.SECSIZE;
		int aSecs = 0;
		
		for(int i = 0; i < sizeSecs; i++) {
			long secEnd = cpos + ISO.SECSIZE;
			FileBuffer mySec = strDat.createReadOnlyCopy(cpos, secEnd);
			
			//Check sector type
			int sh3 = Byte.toUnsignedInt(mySec.getByte(0x12));
			if((sh3 & 0x04) != 0) {
				//Audio
				if(aCh < 0) {
					//Grab channel number.
					aCh = Byte.toUnsignedInt(mySec.getByte(0x11));
					
					int sh4 = Byte.toUnsignedInt(mySec.getByte(0x13));
					
					if((sh4 & 0x01) != 0) aChCount = 2;
					else aChCount = 1;
					if((sh4 & 0x04) != 0) aSampleRate = 18900;
					else aSampleRate = 37800;
					if((sh4 & 0x10) != 0) aBitDepth = 8;
					else aBitDepth = 4;
				}
				mySec.writeToStream(audOut);
				aSecs++;
			}
			else {
				if((sh3 & 0x08) != 0) {
					//Assumed Data (Video)
					if(vCh < 0) {
						//Grab channel number.
						vCh = Byte.toUnsignedInt(mySec.getByte(0x11));
					}
					mySec.writeToStream(vidOut, 0x18, 0x818);
				}
				//Otherwise, empty sector.
			}
			
			mySec.dispose();
			cpos = secEnd;
		}
		
		vidOut.close();
		audOut.close();
		
		//Write metadata...
		vidNode.attr.put(MyupkgConstants.XML_ATTR_STRCHANNEL, Integer.toString(vCh));
		audNode.attr.put(MyupkgConstants.XML_ATTR_STRCHANNEL, Integer.toString(aCh));
		audNode.attr.put(MyupkgConstants.XML_ATTR_SAMPLERATE, Integer.toString(aSampleRate));
		audNode.attr.put(MyupkgConstants.XML_ATTR_BITDEPTH, Integer.toString(aBitDepth));
		
		if(aChCount > 1) audNode.attr.put(MyupkgConstants.XML_ATTR_IS_STEREO, "True");
		else audNode.attr.put(MyupkgConstants.XML_ATTR_IS_STEREO, "False");
		
		//Convert audio to aifc
		FileNode anode = new FileNode(null, "audstr");
		anode.setSourcePath(audPathTemp);
		anode.setOffset(0L);
		anode.setLength(FileBuffer.fileSize(audPathTemp));
		PSXXAStream audraw = PSXXAStream.readStream(anode);
		strf = audraw.getFile(0);
		
		XAAudioStream audioStream = new XAAudioStream(strf, aCh);
		PSXXAAudio.writeAifc(audioStream, 0, aSecs, audPath);
		Files.delete(Paths.get(audPathTemp));
		
		return true;
	}

}
