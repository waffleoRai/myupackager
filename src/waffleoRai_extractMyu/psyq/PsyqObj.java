package waffleoRai_extractMyu.psyq;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class PsyqObj {
	
	private String name;
	private int version;
	private List<PsyqItem> contents;
	
	private Map<Integer, PsyqSection> sections;
	
	public PsyqObj() {
		contents = new LinkedList<PsyqItem>();
		sections = new HashMap<Integer, PsyqSection>();
	}
	
	private void processSections() {
		sections.clear();
		PsyqSection currentSec = null;
		for(PsyqItem item : contents) {
			if(item.getCommand() == PsyqObjCmd.SECTION) {
				currentSec = new PsyqSection();
				currentSec.setSize(0);
				currentSec.setOffset(item.getFilePos());
				currentSec.setName(item.getName());
				currentSec.setId(item.getValue(PsyqItem.FIELDKEY_SECTION));
				sections.put(currentSec.getID(), currentSec);
			}
			else if(item.getCommand() == PsyqObjCmd.SWITCH) {
				int id = item.getValue(PsyqItem.FIELDKEY_SECTION);
				currentSec = sections.get(id);
			}
			else if(item.getCommand() == PsyqObjCmd.EXPORT) {
				int id = item.getValue(PsyqItem.FIELDKEY_SECTION);
				PsyqSection sec = sections.get(id);
				if(sec != null) sec.addSymbol(item);
			}
			else if(item.getCommand() == PsyqObjCmd.BYTES) {
				if(currentSec != null) {
					int amt = item.getValue(PsyqItem.FIELDKEY_SIZE);
					currentSec.setSize(currentSec.getSize() + amt);
				}
			}
			else if(item.getCommand() == PsyqObjCmd.ZEROES) {
				if(currentSec != null) {
					int amt = item.getValue(PsyqItem.FIELDKEY_SIZE);
					currentSec.setSize(currentSec.getSize() + amt);
				}
			}
			else if(item.getCommand() == PsyqObjCmd.UNINIT) {
				int id = item.getValue(PsyqItem.FIELDKEY_SECTION);
				PsyqSection sec = sections.get(id);
				if(sec != null) {
					sec.addSymbol(item);
					int amt = item.getValue(PsyqItem.FIELDKEY_SIZE);
					sec.setSize(sec.getSize() + amt);
				}
			}
		}
	}
	
	public int getVersion() {return version;}
	public String getName() {return name;}
	public void setName(String val) {name = val;}
	
	public PsyqSection getSection(int id) {
		return sections.get(id);
	}
	
	public List<PsyqSection> getAllSections(){
		List<PsyqSection> list = new ArrayList<PsyqSection>(sections.size()+1);
		list.addAll(sections.values());
		return list;
	}
	
	public static PsyqObj parse(BufferReference input) throws UnsupportedFileTypeException {
		if(input == null) return null;
		String mcheck = input.nextASCIIString(3);
		if(mcheck == null || !mcheck.equals("LNK")) return null;
		
		PsyqObj obj = new PsyqObj();
		obj.version = Byte.toUnsignedInt(input.nextByte());
		while(input.hasRemaining()) {
			long offset = input.getBufferPosition();
			PsyqItem item = PsyqItem.parse(input);
			if(item != null) obj.contents.add(item);
			else throw new UnsupportedFileTypeException("PsyqObj.parse || Failed to parse item at 0x" + Long.toHexString(offset));
			if(item.getCommand() == PsyqObjCmd.END) break;
		}
		obj.processSections();
		
		return obj;
	}
	
	public void writeToXML(Writer writer, String indent) throws IOException {
		if(indent == null) indent = "";
		
		writer.write(indent + "<Object");
		if(name != null) writer.write(String.format(" Name=\"%s\"", name));
		writer.write(">\n");
		
		for(PsyqItem item : contents) {
			item.writeToXML(writer, indent + "\t");
		}
		
		writer.write(indent + "</Object>\n");
	}
	
	
}
