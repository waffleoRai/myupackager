package waffleoRai_extractMyu;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import waffleoRai_Compression.lz77.LZMu;
import waffleoRai_Containers.CDDateTime;
import waffleoRai_Containers.CDTable.CDInvalidRecordException;
import waffleoRai_Containers.ISOXAImage;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.MultiFileBuffer;

public class MyuArcCommon {
	
	public static final int COMPR_MODE_FASTEST = 1;
	public static final int COMPR_MODE_SMALL = 4;
	public static final int COMPR_MODE_FFL = 127;
	
	private static Map<String, TypeHandler> callback_map;
	
	public static TypeHandler getTypeHandler(String key){
		if(callback_map == null) return null;
		return callback_map.get(key);
	}
	
	public static void clearTypeHandlerMap(){
		if(callback_map == null) return;
		callback_map.clear();
	}
	
	public static void addTypeHandler(String key, TypeHandler handler){
		if(callback_map == null) callback_map = new HashMap<String, TypeHandler>();
		callback_map.put(key, handler);
	}
	
	public static void loadStandardTypeHandlers(){
		if(callback_map == null){
			callback_map = new HashMap<String, TypeHandler>();
		}
		else callback_map.clear();
		
		TypeHandler unkhndl = new MyuUnkTypeHandler();
		TypeHandler aunkhndl = new MyuUnkAnimeHandler();
		TypeHandler seqhndl = new MyuSeqFile.SoundSeqHandler();
		TypeHandler bnkhndl = new MyuSoundbankFile.SoundBankHandler();
		TypeHandler ibnkhndl = new MyuImagebankFile.MyuImagebankHandler();
		
		callback_map.put(MyupkgConstants.FTYPE_UNK, unkhndl);
		callback_map.put(MyupkgConstants.FTYPE_SEQ, seqhndl);
		callback_map.put(MyupkgConstants.FTYPE_SBNK, bnkhndl);
		callback_map.put(MyupkgConstants.FTYPE_IBNK, ibnkhndl);
		callback_map.put(MyupkgConstants.FTYPE_AUNK, aunkhndl);
		//callback_map.put(MyupkgConstants.FTYPE_IBNK, unkhndl);
		
		//callback_map.put(MyupkgConstants.ASSET_TYPE_IMAGEGROUP, unkhndl);
		callback_map.put(MyupkgConstants.ASSET_TYPE_IMAGEGROUP, ibnkhndl);
		callback_map.put(MyupkgConstants.ASSET_TYPE_SEQ, seqhndl);
		callback_map.put(MyupkgConstants.ASSET_TYPE_BNK, bnkhndl);
		callback_map.put(MyupkgConstants.ASSET_TYPE_ANIMUNK, aunkhndl);
	}
	
	public static long[] readOffsetTable(FileBuffer arcfile){
		if(arcfile == null) return null;
		arcfile.setEndian(false);
		
		int p0 = arcfile.intFromFile(0);
		int file_count = p0 >>> 2;
		long[] offsets = new long[file_count];
		
		long cpos = 0;
		for(int i = 0; i < file_count; i++){
			offsets[i] = Integer.toUnsignedLong(arcfile.intFromFile(cpos)) + cpos;
			cpos += 4;
		}
		
		return offsets;
	}
	
	private static LiteNode readXMLNode(LiteNode parent, Element xml_node){
		if(xml_node == null) return null;
		LiteNode me = null;
		if(parent != null) {
			me = parent.newChild("");
		}
		else me = new LiteNode();
		
		me.name = xml_node.getNodeName();
		me.value = xml_node.getNodeValue();
		
		//Copy attr
		me.attr = XMLReader.getAttributes(xml_node);
		
		//Go through children
		NodeList children = xml_node.getChildNodes();
		int ccount = children.getLength();
		for(int i = 0; i < ccount; i++){
			Node n = children.item(i);
			if(n.getNodeType() == Node.ELEMENT_NODE){
				Element child = (Element)n;
				readXMLNode(me, child);
			}
			else if(n.getNodeType() == Node.TEXT_NODE) {
				if(me.value == null) {
					me.value = n.getTextContent();
					me.value = me.value.trim();
					if(me.value.isEmpty()) me.value = null;
				}
			}
		}
		
		if(me.value != null) {
			//Clean up quotation marks
			while(me.value.startsWith("\"") && me.value.endsWith("\"")) {
				me.value = me.value.substring(1, me.value.length()-1);
				me.value = me.value.trim();
			}
		}
		
		return me;
	}
	
	public static LiteNode readXML(String xmlpath){
		try{
			Document xmldoc = XMLReader.readXMLStatic(xmlpath);
			Node root = xmldoc.getFirstChild();
			LiteNode outroot = null;
			if(root.getNodeType() == Node.ELEMENT_NODE) {
				outroot = readXMLNode(null, (Element)root);
			}
			return outroot;
		}
		catch(Exception ex){
			ex.printStackTrace();
			return null;
		}
	}
	
	private static void writeXMLNode(BufferedWriter bw, LiteNode node, int indents) throws IOException{
		if((node.children != null) && !node.children.isEmpty()){
			//Multiline
			for(int i = 0; i < indents; i++) bw.write("\t");
			bw.write("<" + node.name);
			if(!node.attr.isEmpty()){
				List<String> attrk = new ArrayList<String>(node.attr.size()+1);
				attrk.addAll(node.attr.keySet());
				Collections.sort(attrk);
				for(String key : attrk){
					bw.write(" " + key + "=\"");
					String val = node.attr.get(key);
					bw.write(val + "\"");
				}
			}
			bw.write(">\n");
			
			//Children
			for(LiteNode child : node.children){
				writeXMLNode(bw, child, indents+1);
			}
			
			for(int i = 0; i < indents; i++) bw.write("\t");
			bw.write("</" + node.name + ">\n");
		}
		else{
			//Single line
			for(int i = 0; i < indents; i++) bw.write("\t");
			bw.write("<" + node.name);
			if(!node.attr.isEmpty()){
				List<String> attrk = new ArrayList<String>(node.attr.size()+1);
				attrk.addAll(node.attr.keySet());
				Collections.sort(attrk);
				for(String key : attrk){
					bw.write(" " + key + "=\"");
					String val = node.attr.get(key);
					bw.write(val + "\"");
				}
			}
			if(node.value != null){
				bw.write(">\"" + node.value + "\"</" + node.name + ">");
			}
			else bw.write("/>");
			bw.write("\n");
		}
	}
	
	public static void writeXML(String xmlpath, LiteNode root) throws IOException{
		BufferedWriter bw = new BufferedWriter(new FileWriter(xmlpath));
		bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writeXMLNode(bw, root, 0);
		bw.close();
	}
	
	public static ISOXAImage readISOFile(String isopath) throws IOException, CDInvalidRecordException, UnsupportedFileTypeException {
		//If you just give it the bin image, it will assume...
		// > That it contains the sector header and error correction (0x930 byte sectors)
		
		//It won't assume track 2 (the CDDA file) is either present or absent. It will check the size of the input image.
		//ISO iso = new ISO(FileBuffer.createBuffer(isopath), false);
		//ISOXAImage xa = new ISOXAImage(iso);
		ISOXAImage xa = new ISOXAImage(isopath);
		return xa;
	}
	
	public static String findBIN_fromCUE(String cuepath) {
		//TODO
		return null;
	}
	
	public static FileBuffer bufferGarbageString2Data(String str) {
		if(str == null) return null;
		String[] split = str.split(";");
		int alloc = 0;
		for(int i = 0; i < split.length; i++) {
			if(split[i].startsWith("Z")) {
				try {alloc += Integer.parseInt(split[i].substring(1));}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
				}
			}
			else if(split[i].startsWith("N")) {
				alloc += (split[i].length() - 1) >>> 1;
			}
		}
		
		FileBuffer garbage = new FileBuffer(alloc + 4);
		for(int i = 0; i < split.length; i++) {
			if(split[i].startsWith("Z")) {
				try {
					int zcount = Integer.parseInt(split[i].substring(1));
					for(int j = 0; j < zcount; j++) garbage.addToFile((byte)0);
				}
				catch(NumberFormatException ex) {
					ex.printStackTrace();
				}
			}
			else if(split[i].startsWith("N")) {
				int strlen = split[i].length();
				int pos = 1;
				while(pos < strlen) {
					try {
						int mybyte = Integer.parseInt(split[i].substring(pos, pos+2), 16);
						garbage.addToFile((byte)mybyte);
					}
					catch(NumberFormatException ex) {
						ex.printStackTrace();
					}
					pos += 2;
				}
			}
		}
		
		return garbage;
	}
	
	public static String bufferGarbageData2String(byte[] data) {
		if(data == null) return null;
		boolean sbempty = true;
		boolean nzmode = false;
		StringBuilder sb = new StringBuilder(1024);
		int zstreak = 0;
		for(int i = 0; i < data.length; i++) {
			if(data[i] != 0) {
				if(!nzmode) {
					if(zstreak > 0) {
						sb.append(Integer.toString(zstreak));
					}
					
					if(!sbempty) {
						sb.append(";");
					}
					else sbempty = false;
					sb.append("N");
					nzmode = true;
				}
				
				sb.append(String.format("%02x", Byte.toUnsignedInt(data[i])));
				zstreak = 0;
			}
			else {
				if(zstreak < 1) {
					if(!sbempty) {
						sb.append(";");
					}
					else sbempty = false;
					sb.append("Z");
					nzmode = false;
					zstreak++;
				}
				else zstreak++;
			}
		}
		
		if(zstreak > 0) {
			sb.append(Integer.toString(zstreak));
		}
		return sb.toString();
	}
	
	public static FileBuffer lzDecompress(FileBuffer indata){
		int decsize = indata.intFromFile(0L);
		LZMu decer = new LZMu();
		FileBufferStreamer instr = new FileBufferStreamer(indata);
		for(int i = 0; i < 4; i++) instr.get();
		FileBuffer outdata = decer.decodeToBuffer(instr, decsize);
		return outdata;
	}
	
	public static FileBuffer lzCompress(FileBuffer indata) {
		return lzCompress(indata, COMPR_MODE_SMALL);
	}
	
	public static FileBuffer lzCompress(FileBuffer indata, int mode) {
		if(LzNative.libLoaded()) {
			FileBuffer encdata = LzNative.lzCompressMatch(indata, (int)indata.getFileSize(), mode);
			return encdata;
		}
		else {
			LZMu compr = new LZMu();
			compr.setCompressionStrategy(LZMu.COMP_LOOKAHEAD_QUICK);
			FileBuffer encdata = compr.encode(indata);
			return encdata;
		}
	}

	public static MatchFile lzDecompressMatch(FileBuffer indata) {
		int decsize = indata.intFromFile(0L);
		LZMu decer = new LZMu();
		FileBufferStreamer instr = new FileBufferStreamer(indata);
		for(int i = 0; i < 4; i++) instr.get();
		FileBuffer outdata = decer.decodeToBuffer(instr, decsize);
		
		MatchFile out = new MatchFile();
		out.data = outdata;
		
		byte[] overflow = decer.getOverflowContents();
		if(overflow != null) {
			out.bufferGarbageString = bufferGarbageData2String(overflow);
		}
		
		return out;
	}
	
	public static FileBuffer lzCompressMatch(MatchFile indata) {
		return lzCompressMatch(indata, COMPR_MODE_SMALL);
	}
	
	public static FileBuffer lzCompressMatch(MatchFile indata, int mode) {
		//Glue overflow to end of buffer
		FileBuffer inbuff = indata.data;
		int overflow = 0;
		if(indata.bufferGarbageString != null && !indata.bufferGarbageString.isEmpty()) {
			inbuff = new MultiFileBuffer(2);
			inbuff.addToFile(indata.data);
			FileBuffer garbage = bufferGarbageString2Data(indata.bufferGarbageString);
			inbuff.addToFile(garbage);
			overflow = (int)garbage.getFileSize();
		}
		
		FileBuffer encdata = null;
		if(LzNative.libLoaded()) {
			encdata = LzNative.lzCompressMatch(inbuff, (int)inbuff.getFileSize() - overflow, mode);
		}
		else {
			LZMu compr = new LZMu();
			compr.setCompressionStrategy(LZMu.COMP_LOOKAHEAD_QUICK);
			encdata = compr.encode(inbuff, overflow);
		}
		return encdata;
	}
	
	public static FileBuffer lzCompressMatchForceLit(MatchFile indata, FileBuffer tableFile) {
		//Glue overflow to end of buffer
		FileBuffer inbuff = indata.data;
		int overflow = 0;
		if(indata.bufferGarbageString != null && !indata.bufferGarbageString.isEmpty()) {
			inbuff = new MultiFileBuffer(2);
			inbuff.addToFile(indata.data);
			FileBuffer garbage = bufferGarbageString2Data(indata.bufferGarbageString);
			inbuff.addToFile(garbage);
			overflow = (int)garbage.getFileSize();
		}
		
		FileBuffer encdata = null;
		if(LzNative.libLoaded()) {
			encdata = LzNative.lzCompressMatchForceLit(inbuff, (int)inbuff.getFileSize() - overflow, tableFile);
		}
		else {
			LZMu compr = new LZMu();
			compr.setCompressionStrategy(LZMu.COMP_LOOKAHEAD_QUICK);
			encdata = compr.encode(inbuff, overflow);
		}
		return encdata;
	}
	
	public static String[][] loadChecksumTable(String path) throws IOException {
		List<String> lines = new LinkedList<String>();
		if(!FileBuffer.fileExists(path)) return null;
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.isEmpty()) continue;
			if(line.startsWith("#")) continue;
			lines.add(line);
		}
		br.close();
		
		int ecount = lines.size();
		if(ecount < 1) return null;
		String[][] tbl = new String[ecount][3];
		int i = 0;
		for(String l : lines) {
			String[] fields = l.split(",");
			for(int j = 0; j < fields.length; j++) {
				if(j >= 3) break;
				tbl[i][j] = fields[j];
				
				if(j == 2) tbl[i][j] = tbl[i][j].toUpperCase();
			}
			i++;
		}
		
		return tbl;
	}
	
	public static String datetime2XMLVal(CDDateTime timestamp) {
		StringBuilder sb = new StringBuilder(128);
		sb.append(String.format("%04d/", timestamp.getYear()));
		sb.append(String.format("%02d/", timestamp.getMonth()));
		sb.append(String.format("%02d ", timestamp.getDay()));
		sb.append(String.format("%02d:", timestamp.getHour()));
		sb.append(String.format("%02d:", timestamp.getMinute()));
		sb.append(String.format("%02d:", timestamp.getSecond()));
		sb.append(String.format("%02d ", timestamp.getFrame()));
		int tz = timestamp.getTimezone();
		if(tz > 0) sb.append("+");
		else if(tz < 0) sb.append("-");
		sb.append(Integer.toString(tz));
		
		return sb.toString();
	}
	
	public static String getContainingDir(String path) {
		if(!path.contains(File.separator)) return ".";
		return path.substring(0, path.lastIndexOf(File.separator));
	}
	
	public static String getFilename(String path) {
		if(!path.contains(File.separator)) return path;
		return path.substring(path.lastIndexOf(File.separator)+1);
	}
	
	public static String getSystemAbsolutePath(String relPath) {
		if(relPath == null) return null;
		Path p = Paths.get(relPath);
		return p.toAbsolutePath().toString();
	}
	
	public static String localPath2UnixRel(String ref_path, String trg_path) {
		//Unix style relative path for trg_path relative to ref_path
		//if ref_path is a file, strip to parent directory
		if(!FileBuffer.directoryExists(ref_path)) {
			int cidx = ref_path.lastIndexOf(File.separatorChar);
			if(cidx >= 0) ref_path = ref_path.substring(0, cidx);
		}
		
		if(!ref_path.contains(File.separator)) ref_path = "." + File.separator + ref_path;
		if(!trg_path.contains(File.separator)) trg_path = "." + File.separator + trg_path;
		
		String SEP = File.separator;
		if(File.separatorChar == '\\') {
			SEP = "\\\\";
		}
		
		String[] ref_parts = ref_path.split(SEP);
		String[] trg_parts = trg_path.split(SEP);
		int matched = 0;
		int scanlen = (ref_parts.length>trg_parts.length)?trg_parts.length:ref_parts.length;
		for(int i = 0; i < scanlen; i++) {
			if(ref_parts[i].equalsIgnoreCase(trg_parts[i])) {
				matched++;
			}
			else break;
		}
		
		//Is the target in the same dir or a subdir of the ref?
		if(matched == ref_parts.length) {
			String relpath = ".";
			for(int i = matched; i < trg_parts.length; i++) {
				relpath += "/" + trg_parts[i];
			}
			return relpath;
		}
		else {
			//Add a .. dir for every dir back from ref to last match
			int backcount = ref_parts.length - matched;
			String relpath = "";
			for(int i = 0; i < backcount; i++) {
				if(i > 0) relpath += "/..";
				else relpath += "..";
			}
			for(int i = matched; i < trg_parts.length; i++) {
				relpath += "/" + trg_parts[i];
			}
			return relpath;
		}
	}
	
	public static String unixRelPath2Local(String wd, String trg) {
		String SEP = File.separator;
		if(File.separatorChar == '\\') {
			SEP = "\\\\";
		}
		
		if(trg == null) return wd;
		
		String[] wdparts = wd.split(SEP);
		String[] trgparts = trg.split("/");
		
		int wdpos = wdparts.length-1;
		int trgpos = 0;
		for(int i = 0; i < trgparts.length; i++) {
			if(trgparts[i].equals(".")) {
				trgpos++;
			}
			else if(trgparts[i].equals("..")) {
				trgpos++;
				wdpos--;
			}
			else break;
		}
		
		LinkedList<String> list = new LinkedList<String>();
		for(int i = 0; i <= wdpos; i++) list.add(wdparts[i]);
		for(int i = trgpos; i < trgparts.length; i++) list.add(trgparts[i]);
		
		int alloc = 0;
		LinkedList<String> list2 = new LinkedList<String>();
		for(String s : list) {
			if(s.equals(".")) continue;
			else if(s.equals("..")) {
				if(!list2.isEmpty()) list2.removeLast();
			}
			else {
				list2.add(s);
				alloc += s.length();
			}
		}
		list.clear();
		
		StringBuilder sb = new StringBuilder(alloc + 1);
		boolean first = true;
		for(String s : list2) {
			if(!first) sb.append(File.separatorChar);
			else first = false;
			sb.append(s);
		}
		list2.clear();
		
		return sb.toString();
	}
	
	public static int parseInt(String str) {
		if(str.startsWith("0x")) {
			return Integer.parseUnsignedInt(str.substring(2), 16);
		}
		else return Integer.parseInt(str);
	}
	
	public static long parseLong(String str) {
		if(str.startsWith("0x")) {
			return Long.parseUnsignedLong(str.substring(2), 16);
		}
		else return Long.parseLong(str);
	}
	
}
