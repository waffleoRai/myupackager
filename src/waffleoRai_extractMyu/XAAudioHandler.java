package waffleoRai_extractMyu;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import waffleoRai_Containers.ISO;
import waffleoRai_Files.psx.XAStreamFile;
import waffleoRai_Sound.AiffFile;
import waffleoRai_Sound.psx.PSXXAAudio;
import waffleoRai_Sound.psx.XAAudioStream;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.StringUtils;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class XAAudioHandler{
	
	private static class StrClip implements Comparable<StrClip>{
		public String eName;
		public String path;
		
		public int xaFile;
		public int ch;
		
		public int sizeSecs;
		public int uid;
		public int chStartSec; //Total stream position calculated from this and channel index/interleave type
		
		public int compareTo(StrClip o) {
			if(o == null) return -1;
			return uid - o.uid;
		}
	}
	
	private static class ClipGroup{
		public String eName;
		public List<StrClip> clips;
	}
	
	public String wd;
	public LiteNode import_spec;
	
	public boolean doubleSpeed = true;
	public boolean isStereo = false;
	public boolean srHalf = false;
	public boolean bdDouble = false;
	
	//In group table, first entry is a dummy that has the group count. Last entry is stream end sector.
	private List<ClipGroup> groups;
	
	private int ileavDenom = 16; //If 16, run through channels 0-15. If 8, 0-7. If 32 run through 0-15, write 16 dummies, then run again.
	private String[] tempPaths;
	private OutputStream[] chOutputHandles;
	private int[] outputAmt; //How many sectors output to each channel
	private int totalSec; //Total secs output
	
	private int nextOutputChannel() {
		if(outputAmt == null) return -1;
		int min = Integer.MAX_VALUE;
		int minIdx = -1;
		for(int i = 0; i < outputAmt.length; i++) {
			if(outputAmt[i] < min) {
				min = outputAmt[i];
				minIdx = i;
			}
		}
		return minIdx;
	}
	
	private void processInSpecs() {
		MyuPackagerLogger.logMessage("XAAudioHandler.processInSpecs", 
				"Parsing input specs...");
		
		//LiteNodes to ClipGroups
		//Also calculate playback speed etc.
		int chCount = 16;
		
		String aval = import_spec.attr.get(MyupkgConstants.XML_ATTR_BITDEPTH);
		if(aval != null && !aval.equals("4")) bdDouble = true;
		aval = import_spec.attr.get(MyupkgConstants.XML_ATTR_SAMPLERATE);
		if(aval != null && !aval.equals("37800")) srHalf = true;
		aval = import_spec.attr.get(MyupkgConstants.XML_ATTR_IS_STEREO);
		if(aval != null && aval.equalsIgnoreCase("true")) isStereo = true;
		aval = import_spec.attr.get(MyupkgConstants.XML_ATTR_DBLSPEED);
		if(aval != null && aval.equalsIgnoreCase("true")) doubleSpeed = true;
		else doubleSpeed = false;
		
		if(isStereo && !srHalf) {
			chCount = 8;
			ileavDenom = 8;
		}
		
		if(srHalf && !isStereo) {
			//1/32. May not actually work. Who knows.
			ileavDenom = 32;
		}
		
		outputAmt = new int[chCount];
		chOutputHandles = new OutputStream[chCount];
		tempPaths = new String[chCount];
		
		groups = new ArrayList<ClipGroup>(import_spec.children.size()+1);
		int g = 0;
		for(LiteNode groupNode : import_spec.children) {
			if(groupNode.name == null) continue;
			if(!groupNode.name.equals(MyupkgConstants.ASSET_TYPE_CLIPGROUP)) {
				MyuPackagerLogger.logMessage("XAAudioHandler.processInSpecs", 
						"WARNING: Only clip groups allowed at top level! Skipping...");
				continue;
			}
			
			ClipGroup group = new ClipGroup();
			group.eName = groupNode.attr.get(MyupkgConstants.XML_ATTR_ENUM);
			if(group.eName == null) {
				group.eName = String.format("ClipGroup_%02x", g);
			}
			
			int gchildCount = groupNode.children.size();
			group.clips = new ArrayList<StrClip>(gchildCount+1);
			int c = 0;
			for(LiteNode clipNode : groupNode.children) {
				if(clipNode.name == null) continue;
				if(!clipNode.name.equals(MyupkgConstants.ASSET_TYPE_XAAUDIO)) {
					MyuPackagerLogger.logMessage("XAAudioHandler.processInSpecs", 
							"WARNING: Only audio clips allowed in clip groups! Skipping...");
					continue;
				}
				
				StrClip clip = new StrClip();
				clip.eName = clipNode.attr.get(MyupkgConstants.XML_ATTR_ENUM);
				if(clip.eName == null) {
					clip.eName = String.format("AClip_%02x_%02x", g, c);
				}
				
				aval = clipNode.attr.get(MyupkgConstants.XML_ATTR_SAMPLEID);
				if(aval != null) clip.uid = MyuArcCommon.parseInt(aval);
				else clip.uid = c;
				
				aval = clipNode.attr.get(MyupkgConstants.XML_ATTR_XAFILEIDX);
				if(aval != null) clip.xaFile = MyuArcCommon.parseInt(aval);
				clip.path = clipNode.value;
				
				group.clips.add(clip);
				c++;
			}
			
			groups.add(group);
			g++;
		}
		
	}

	private void initOutput(String outpath) throws IOException {
		Arrays.fill(outputAmt, 0);
		for(int i = 0; i < outputAmt.length; i++) {
			tempPaths[i] = outpath + String.format("_%02d.tmp", i);
			chOutputHandles[i] = new BufferedOutputStream(new FileOutputStream(tempPaths[i]));
		}
		totalSec = 0;
	}
	
	private void closeTempChannels() throws IOException {
		for(int i = 0; i < chOutputHandles.length; i++) {
			if(chOutputHandles[i] != null) chOutputHandles[i].close();
		}
	}
	
	public void buildStream(String outpath) throws IOException, UnsupportedFileTypeException {
		processInSpecs();
		initOutput(outpath);
		
		final int DAT_SEC_SIZE = PSXXAAudio.BLOCK_SIZE * PSXXAAudio.BLOCKS_PER_SEC;
		
		//individual channels
		byte[] buffer = MyuCD.genDummySecBase_M2F2();
		int audioFlags = 0;
		if(isStereo) audioFlags |= (1 << 0);
		if(srHalf) audioFlags |= (1 << 2);
		if(bdDouble) audioFlags |= (1 << 4);
		
		buffer[0x10] = (byte)(0); //Fill In
		buffer[0x11] = (byte)(0); //Fill In
		buffer[0x12] = (byte)(0x64);
		buffer[0x13] = (byte) audioFlags;
		for(int k = 0; k < 4; k++) buffer[0x14 + k] = buffer[0x10 + k];
		
		StrClip[] lastClip = new StrClip[outputAmt.length];
		for(ClipGroup group : groups) {
			MyuPackagerLogger.logMessage("XAAudioHandler.buildStream", 
					"Working on audio clip group: " + group.eName);
			
			for(StrClip clip : group.clips) {
				clip.ch = nextOutputChannel();
				clip.chStartSec = outputAmt[clip.ch];
				clip.sizeSecs = 0;
				
				String fullpath = MyuArcCommon.unixRelPath2Local(wd, clip.path);
				FileBuffer fileDat = FileBuffer.createBuffer(fullpath, true);
				AiffFile aifc = AiffFile.readAiff(fileDat);
				fileDat.dispose();
				if(aifc.getCompressionId() != PSXXAAudio.AIFC_ID_XAAUD) {
					MyuPackagerLogger.logMessage("XAAudioHandler.export", 
							"File \"" + fullpath + "\" is not compatible for import! Skipping...");
					continue;
				}
				byte[] audioData = aifc.getAifcRawSndData();
				clip.sizeSecs = (audioData.length + (DAT_SEC_SIZE-1)) / DAT_SEC_SIZE;
				
				buffer[0x10] = (byte)(clip.xaFile + 1);
				buffer[0x11] = (byte)(clip.ch); //Fill In
				for(int k = 0; k < 4; k++) buffer[0x14 + k] = buffer[0x10 + k];
				
				int datPos = 0;
				for(int s = 0; s < clip.sizeSecs; s++) {
					for(int k = 0; k < DAT_SEC_SIZE; k++) {
						if(datPos < audioData.length) {
							buffer[0x18 + k] = audioData[datPos++];
						}
						else buffer[0x18 + k] = 0;
					}
					
					chOutputHandles[clip.ch].write(buffer);
					outputAmt[clip.ch]++;
				}
				lastClip[clip.ch] = clip;
			}
		}
		
		//Zero fill channels until all are the same length.
		MyuPackagerLogger.logMessage("XAAudioHandler.buildStream", 
				"Padding channels to equal length...");
		
		byte lastfile = buffer[0x10];
		MyuCD.loadEmptyXAAudSec(buffer);
		buffer[0x10] = lastfile;
		buffer[0x11] = (byte)(0); //Fill In
		buffer[0x12] = (byte)(0x64);
		buffer[0x13] = (byte) audioFlags;
		for(int k = 0; k < 4; k++) buffer[0x14 + k] = buffer[0x10 + k];
		
		int cSecs = 0;
		int longestCh = -1;
		for(int i = 0; i < outputAmt.length; i++) {
			//Add one extra empty sector?
			/*buffer[0x11] = (byte)(i);
			buffer[0x15] = buffer[0x11];
			chOutputHandles[i].write(buffer);
			outputAmt[i]++;*/
			
			if(outputAmt[i] > cSecs) {
				cSecs = outputAmt[i];
				longestCh = i;
			}
		}
		for(int i = 0; i < outputAmt.length; i++) {
			buffer[0x11] = (byte)(i);
			buffer[0x15] = buffer[0x11];
			while(outputAmt[i] < cSecs) {
				chOutputHandles[i].write(buffer);
				outputAmt[i]++;
			}
		}
		lastClip[longestCh].sizeSecs++; //Dunno why???
		
		closeTempChannels();
		
		//Combine (delete temps)
		MyuPackagerLogger.logMessage("XAAudioHandler.buildStream", 
				"Interleaving channels to stream file...");
		
		InputStream[] chInputHandles = new InputStream[chOutputHandles.length];
		for(int i = 0; i < chInputHandles.length; i++) {
			chInputHandles[i] = new BufferedInputStream(new FileInputStream(tempPaths[i]));
		}
		
		byte[] emptySec = buffer;
		buffer = new byte[ISO.SECSIZE];
		int lastC = ileavDenom - 1;
		int lastS = cSecs - 1;
		
		BufferedOutputStream mainOut = new BufferedOutputStream(new FileOutputStream(outpath));
		for(int i = 0; i < cSecs; i++) {
			for(int c = 0; c < ileavDenom; c++) {
				if(c < chInputHandles.length) {
					chInputHandles[c].read(buffer);
					if((c == lastC) && (i == lastS)) {
						buffer[0x12] = (byte)(Byte.toUnsignedInt(buffer[0x12]) | 0x80);
						buffer[0x16] = buffer[0x12];
					}
					mainOut.write(buffer);
				}
				else {
					//Dummy
					emptySec[0x11] = (byte)(c >>> 1);
					emptySec[0x15] = emptySec[0x11];
					if((c == lastC) && (i == lastS)) {
						emptySec[0x12] = (byte)(Byte.toUnsignedInt(emptySec[0x12]) | 0x80);
						emptySec[0x16] = emptySec[0x12];
					}
					mainOut.write(emptySec);
				}
				
				totalSec++;
			}
		}
		mainOut.close();
		
		MyuPackagerLogger.logMessage("XAAudioHandler.buildStream", 
				"Cleaning up...");
		for(int i = 0; i < chInputHandles.length; i++) {
			if(chInputHandles[i] != null) chInputHandles[i].close();
			Files.deleteIfExists(Paths.get(tempPaths[i]));
		}
	}
	
	public void exportH(String inclDir, String hPath, String subdir) throws IOException {
		final int LARGE_GROUP_SIZE = 32;
		
		String arcName = import_spec.attr.get(MyupkgConstants.XML_ATTR_NAME);
		if(arcName == null) arcName = "VOICE";
		arcName = arcName.toLowerCase();
		arcName = StringUtils.capitalize(arcName);
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(hPath));
		bw.write("#ifndef RESARC_" + arcName.toUpperCase() + "_H\n");
		bw.write("#define RESARC_" + arcName.toUpperCase() + "_H\n\n");
		
		bw.write("/* ---------------------------------------------\n");
		bw.write("*  Autogenerated by MyuPackager arcpack\n");
		bw.write("*  ---------------------------------------------*/\n\n");
		
		bw.write("#include \"data/DataTables.h\"\n\n");
		
		//Write the larger groups to external header files.
		//boolean anyExtHeaders = false;
		String subDirRel = MyuArcCommon.localPath2UnixRel(inclDir, subdir);
		if(subDirRel.startsWith("./")) subDirRel = subDirRel.substring(2);
		for(ClipGroup g : groups) {
			if(g.clips.size() > LARGE_GROUP_SIZE) {
				//anyExtHeaders = true;
				String name = g.eName;
				String gpath = subdir + File.separator + name + ".h";
				if(!FileBuffer.directoryExists(subdir)) {
					Files.createDirectories(Paths.get(subdir));
				}
				
				bw.write("#include \"" + subDirRel + "/" + name + ".h\"\n");
				BufferedWriter bw2 = new BufferedWriter(new FileWriter(gpath));
				bw2.write("#ifndef CLIPGRP_" + name.toUpperCase() + "_H\n");
				bw2.write("#define CLIPGRP_" + name.toUpperCase() + "_H\n\n");
				bw2.write("/* ---------------------------------------------\n");
				bw2.write("*  Autogenerated by MyuPackager arcpack\n");
				bw2.write("*  ---------------------------------------------*/\n\n");
				
				bw2.write("#ifdef __cplusplus\n");
				bw2.write("extern \"C\" {\n");
				bw2.write("#endif\n\n");
				bw2.write("typedef enum {\n");
				List<StrClip> clips = new ArrayList<StrClip>(g.clips.size());
				clips.addAll(g.clips);
				Collections.sort(clips);
				int lastId = -1;
				for(StrClip clip : clips) {
					bw2.write(String.format("\t/*%03d 0x%02x*/\t", clip.uid, clip.uid));
					bw2.write(clip.eName);
					if((lastId < 0) || (clip.uid > (lastId + 1))) {
						bw2.write(" = " + clip.uid);
					}
					lastId = clip.uid;
					bw2.write(",\n");
				}
				bw2.write("} " + name + "XAClip;\n\n");
				
				bw2.write("#ifdef __cplusplus\n");
				bw2.write("}\n");
				bw2.write("#endif\n\n");
				
				bw2.write("#endif\n");
				bw2.close();
			}
		}
		bw.write("\n");
		bw.write("#ifdef __cplusplus\n");
		bw.write("extern \"C\" {\n");
		bw.write("#endif\n\n");
		
		String gtblName = import_spec.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_GRP);
		if(gtblName == null) gtblName = "gVoiceGroupPos";
		String ctblName = import_spec.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_CLIP);
		if(ctblName == null) ctblName = "gVoiceClipInfo";
		
		bw.write("extern uint16_t " + gtblName + "[];\n");
		bw.write("extern VoiceEntry " + ctblName + "[];\n\n");
		
		//Groups enum
		int groupId = 1; //Assign first to 1 (TODO: Need to update other scripts to ignore first and last supposed groups)
		bw.write("typedef enum {\n");
		for(ClipGroup g : groups) {
			bw.write(String.format("\t/*0x%02x (%03d)*/\t", groupId, groupId));
			bw.write(g.eName);
			if(groupId == 1) bw.write(" = 1");
			bw.write(",\n");
			groupId++;
		}
		bw.write("} Res" + arcName + "Group;\n\n");
		
		//Enums for smaller groups
		for(ClipGroup g : groups) {
			if(g.clips.size() <= LARGE_GROUP_SIZE) {
				String name = g.eName;
				bw.write("typedef enum {\n");
				List<StrClip> clips = new ArrayList<StrClip>(g.clips.size());
				clips.addAll(g.clips);
				Collections.sort(clips);
				int lastId = -1;
				for(StrClip clip : clips) {
					bw.write(String.format("\t/*%03d 0x%02x*/\t", clip.uid, clip.uid));
					bw.write(clip.eName);
					if((lastId < 0) || (clip.uid > (lastId + 1))) {
						bw.write(" = " + clip.uid);
					}
					lastId = clip.uid;
					bw.write(",\n");
				}
				bw.write("} " + name + "XAClip;\n\n");
			}
		}
		
		//Close
		bw.write("#ifdef __cplusplus\n");
		bw.write("}\n");
		bw.write("#endif\n\n");
		bw.write("#endif\n");
		bw.close();
	}
	
	public void exportC(String path, String hRel) throws IOException {
		String gtblName = import_spec.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_GRP);
		if(gtblName == null) gtblName = "gVoiceGroupPos";
		String ctblName = import_spec.attr.get(MyupkgConstants.XML_ATTR_TBLNAME_CLIP);
		if(ctblName == null) ctblName = "gVoiceClipInfo";
		
		if(hRel.startsWith("./")) hRel = hRel.substring(2);
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(path));
		
		bw.write("/* ---------------------------------------------\n");
		bw.write("*  Autogenerated by MyuPackager arcpack\n");
		bw.write("*  ---------------------------------------------*/\n\n");
		
		bw.write("#include \"" + hRel + "\"\n\n");
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("extern \"C\" {\n");
		bw.write("#endif\n\n");
		
		int col = 1;
		int gPos = 0;
		bw.write("\tuint16_t " + gtblName + "[] = {\n");
		bw.write(String.format("\t\t0x%04x, ", groups.size()));
		for(ClipGroup group : groups) {
			if(col == 0) bw.write("\n\t\t");
			bw.write(String.format("0x%04x, ", gPos));
			col = (col + 1) & 0x7;
			gPos += group.clips.size();
		}
		bw.write(String.format("0x%04x, ", gPos)); //End of last group
		bw.write("\n\t};\n\n");
		
		bw.write("\tVoiceEntry " + ctblName + "[] = {");
		col = 0;
		for(ClipGroup group : groups) {
			for(StrClip clip : group.clips) {
				if(col == 0) bw.write("\n\t\t");
				bw.write(String.format("{0x%02x, ", clip.uid));
				bw.write(String.format("0x%02x, ", clip.ch));
				
				int totalStart = clip.chStartSec << 4;
				totalStart += clip.ch;
				bw.write(String.format("0x%05x, ", totalStart));
				bw.write(String.format("0x%04x}, ", (clip.sizeSecs - 3)));
				col = (col + 1) & 0x3;
			}
		}

		bw.write("\n\t};\n\n");
		
		bw.write("#ifdef __cplusplus\n");
		bw.write("}\n");
		bw.write("#endif\n\n");
		
		bw.close();
	}
	
	//Can't do regular type handler because of interleaving...
	
	public static boolean export(ExportContext ctx) throws IOException {
		String fileName = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
		String enumName = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_ENUM);
		
		int channel = 1;
		int startSec = 0;
		int sizeSecs = 0;
		int sampleId = 0;
		int xaFile = 0;
		
		String aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_STRCHANNEL);
		if(aval != null) channel = MyuArcCommon.parseInt(aval);
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_SAMPLEID);
		if(aval != null) sampleId = MyuArcCommon.parseInt(aval);
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_XAFILEIDX);
		if(aval != null) xaFile = MyuArcCommon.parseInt(aval);
		
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_SECCOUNT);
		if(aval != null) sizeSecs = MyuArcCommon.parseInt(aval);
		else {
			MyuPackagerLogger.logMessage("XAAudioHandler.export", 
					"Sector count required! Otherwise cannot find clip.");
			return false;
		}
		
		aval = ctx.target_in.attr.get(MyupkgConstants.XML_ATTR_START);
		if(aval != null) startSec = MyuArcCommon.parseInt(aval);
		else {
			MyuPackagerLogger.logMessage("XAAudioHandler.export", 
					"Start sector required! Otherwise cannot find clip.");
			return false;
		}
		
		if(enumName == null) {
			enumName = "AudioClip_" + channel + "_" + startSec;
		}
		if(fileName == null) {
			fileName = enumName.toUpperCase();
		}
		
		MyuPackagerLogger.logMessage("XAAudioHandler.export", 
				"Working on audio clip " + fileName + "...");
		
		//ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_FILENAME, fileName);
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_ENUM, enumName);
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_STRCHANNEL, Integer.toString(channel));
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_SAMPLEID, String.format("0x%02x", sampleId));
		ctx.target_out.attr.put(MyupkgConstants.XML_ATTR_XAFILEIDX, Integer.toString(xaFile));
		
		String outpath = ctx.output_dir + File.separator + fileName + ".aifc";
		ctx.target_out.value = MyuArcCommon.localPath2UnixRel(ctx.xml_wd, outpath);
		
		XAStreamFile strf = ctx.xaStr.getFile(xaFile);
		XAAudioStream astr = new XAAudioStream(strf, channel);
		PSXXAAudio.writeAifc(astr, startSec, sizeSecs, outpath);
		
		return true;
	}

}
