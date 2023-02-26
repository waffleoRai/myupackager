package waffleoRai_extractMyu;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import waffleoRai_Compression.lz77.LZMu;
import waffleoRai_Compression.lz77.LZMu.LZMuDef;
import waffleoRai_Files.XMLReader;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBufferStreamer;
import waffleoRai_Utils.StreamWrapper;

public class MyuArcCommon {
	
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
		
		callback_map.put(MyupkgConstants.FTYPE_UNK, new MyuUnkTypeHandler());
		callback_map.put(MyupkgConstants.FTYPE_SEQ, new MyuSeqFile.SoundSeqHandler());
		callback_map.put(MyupkgConstants.FTYPE_SBNK, new MyuSoundbankFile.SoundBankHandler());
		callback_map.put(MyupkgConstants.FTYPE_IBNK, new MyuImagebankFile.MyuImagebankHandler());
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
		LiteNode me = new LiteNode();
		me.parent = parent;
		parent.children.add(me);
		
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
		}
		
		return me;
	}
	
	public static LiteNode readXML(String xmlpath){
		try{
			Document xmldoc = XMLReader.readXMLStatic(xmlpath);
			Node root = xmldoc.getFirstChild();
			LiteNode outroot = new LiteNode();
			
			outroot.name = root.getNodeName();
			NodeList children = root.getChildNodes();
			int ccount = children.getLength();
			for(int i = 0; i < ccount; i++){
				Node n = children.item(i);
				if(n.getNodeType() == Node.ELEMENT_NODE){
					Element child = (Element)n;
					readXMLNode(outroot, child);
				}
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
				Collections.sort(attrk);
				for(String key : attrk){
					bw.write(" " + key + "=\"");
					String val = node.attr.get(key);
					bw.write(val + "\"");
				}
			}
			if(node.value != null){
				bw.write(">" + node.value + "</" + node.name + ">");
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
	
	public static FileBuffer lzDecompress(FileBuffer indata){
		int decsize = indata.intFromFile(0L);
		FileBuffer outdata = new FileBuffer(decsize, false);
		LZMuDef lzdef = LZMu.getDefinition();
		StreamWrapper dec = lzdef.decompress(new FileBufferStreamer(indata));
		while(!dec.isEmpty()) outdata.addToFile(dec.get());
		
		return outdata;
	}

}
