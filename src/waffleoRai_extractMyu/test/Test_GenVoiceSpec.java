package waffleoRai_extractMyu.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.MyuCode;
import waffleoRai_extractMyu.MyupkgConstants;
import waffleoRai_extractMyu.tables.SymbolInfo;
import waffleoRai_extractMyu.tables.SymbolList;

public class Test_GenVoiceSpec {
	
	private static class VoiceEntry{
		public int id;
		public int ch;
		public int start;
		public int size;
	}
	
	private static int[] readGT(FileBuffer gt) {
		int gtsize = (int)(gt.getFileSize() >> 1);
		int[] table = new int[gtsize];
		gt.setCurrentPosition(0L);
		for(int i = 0; i < gtsize; i++) {
			table[i] = Short.toUnsignedInt(gt.nextShort());
		}
		
		return table;
	}
	
	private static VoiceEntry[] readCT(FileBuffer ct) {
		int ctsize = (int)(ct.getFileSize() / 6);
		VoiceEntry[] table = new VoiceEntry[ctsize];
		ct.setCurrentPosition(0L);
		for(int i = 0; i < ctsize; i++) {
			VoiceEntry ve = new VoiceEntry();
			table[i] = ve;
			ve.id = Byte.toUnsignedInt(ct.nextByte());
			ve.ch = Byte.toUnsignedInt(ct.nextByte());
			int word = ct.nextInt();
			ve.start = word & 0x3ffff;
			ve.size = (word >>> 18) & 0x3fff;
		}
		
		return table;
	}

	public static void main(String[] args) {
		String outdir = args[0];
		String exepath = args[1]; //Get tables
		String varSymListPath = args[2]; //Get addresses
		
		final String groupTableName = "gVoiceGroupPos";
		final String clipTableName = "gVoiceClipInfo";
		
		try {
			//Open inputs.
			List<SymbolInfo> symbols = SymbolList.readSplatSymbolList(varSymListPath);
			SymbolInfo gtSym = null;
			SymbolInfo ctSym = null;
			for(SymbolInfo sym : symbols) {
				if(sym.name == null) continue;
				if(sym.name.equals(groupTableName)) gtSym = sym;
				else if(sym.name.equals(clipTableName)) ctSym = sym;
			}
			
			//Load binary tables
			long stOff = MyuCode.address2ExeOffset(gtSym.address);
			long edOff = stOff + gtSym.sizeBytes;
			FileBuffer gt = FileBuffer.createBuffer(exepath, stOff, edOff, false);
			stOff = MyuCode.address2ExeOffset(ctSym.address);
			edOff = stOff + ctSym.sizeBytes;
			FileBuffer ct = FileBuffer.createBuffer(exepath, stOff, edOff, false);
			
			//Open output
			String mainXmlPath = outdir + File.separator + "voice.xml";
			BufferedWriter bw = new BufferedWriter(new FileWriter(mainXmlPath));
			bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			bw.write("<ArcSpec");
			bw.write(" Name=\"VOICE\"");
			bw.write(" Enum=\"VoiceStr\"");
			//bw.write(" IndexingType=\"Sector\"");
			bw.write(" XAStream=\"True\"");
			bw.write(" AudioChannels=\"16\"");
			bw.write(" IsStereo=\"False\""); //Maybe make these per sample instead?
			bw.write(" SampleRate=\"37800\"");
			bw.write(" BitDepth=\"4\"");
			bw.write(" DoubleSpeed=\"True\"");
			bw.write(" HasXAVideo=\"False\"");
			bw.write(" HasXAAudio=\"True\"");
			bw.write(">\n");
			
			int[] groupTable = readGT(gt); gt.dispose();
			VoiceEntry[] clipTable = readCT(ct); ct.dispose();
			int groupCount = groupTable.length - 1;
			for(int g = 0; g < groupCount; g++) {
				bw.write(String.format("\t<%s", MyupkgConstants.XML_NODENAME_CLIPGROUP));
				bw.write(String.format(" %s=\"VOXGRP_%02X\"", MyupkgConstants.XML_ATTR_NAME, g));
				bw.write(String.format(" %s=\"VoiceGroup_%02x\"", MyupkgConstants.XML_ATTR_ENUM, g));

				int cStart = groupTable[g];
				int cEnd = groupTable[g+1];
				if(cStart >= cEnd) {
					bw.write("/>\n");
					continue;
				}
				bw.write(">\n");
				
				for(int i = cStart; i < cEnd; i++) {
					VoiceEntry ve = clipTable[i];
					bw.write(String.format("\t\t<%s", MyupkgConstants.XML_NODENAME_XA_AUDIOCLIP));
					bw.write(String.format(" %s=\"VOICE%02X_%02X\"", MyupkgConstants.XML_ATTR_FILENAME, g, ve.id));
					bw.write(String.format(" %s=\"Voxg%02x_%02x\"", MyupkgConstants.XML_ATTR_ENUM, g, ve.id));
					bw.write(String.format(" %s=\"%d\"", MyupkgConstants.XML_ATTR_STRCHANNEL, ve.ch));
					bw.write(String.format(" %s=\"0x%02x\"", MyupkgConstants.XML_ATTR_SAMPLEID, ve.id));
					//bw.write(String.format(" %s=\"%d\"", MyupkgConstants.XML_ATTR_START, ve.start));
					bw.write(String.format(" %s=\"%d\"", MyupkgConstants.XML_ATTR_START, (ve.start >>> 4))); //Make channel relative?
					bw.write(String.format(" %s=\"%d\"", MyupkgConstants.XML_ATTR_SECCOUNT, (ve.size + 3)));
					bw.write("/>\n");
				}
				
				bw.write(String.format("\t</%s>\n", MyupkgConstants.XML_NODENAME_CLIPGROUP));
			}
			
			bw.write("</ArcSpec>\n");
			bw.close();
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

	}

}
