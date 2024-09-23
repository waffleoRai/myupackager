package waffleoRai_extractMyu.tables;

import java.util.ArrayList;
import java.util.List;

import waffleoRai_extractMyu.LiteNode;

public class StructDef {
	
	public static final int MEMBER_TYPE_FIELD = 0;
	public static final int MEMBER_TYPE_BITFIELD = 1;
	
	public static class StructMember{
		public int memType = MEMBER_TYPE_FIELD;
		public String typeName;
		public String name;
		public int size;
		
		public int getHexDigits() {
			int bits = size;
			if(memType == MEMBER_TYPE_FIELD) {
				bits <<= 3;
			}
			return (bits + 0x3) >>> 2;
		}
	}
	
	public List<StructMember> members;
	public String name;
	public int size; //Bytes
	public int perLine = 1;
	
	public StructDef() {
		members = new ArrayList<StructMember>(8);
	}
	
	public static StructDef fromXMLNode(LiteNode node) {
		if(node == null) return null;
		if(node.name == null) return null;
		if(!node.name.equals("StructDef")) return null;
		
		StructDef def = new StructDef();
		def.name = node.attr.get("Name");
		
		boolean sizeDefined = false;
		String aval = node.attr.get("Size");
		if(aval != null) {
			if(aval.startsWith("0x")) def.size = Integer.parseUnsignedInt(aval.substring(2), 16);
			else def.size = Integer.parseUnsignedInt(aval);
			sizeDefined = true;
		}
		
		aval = node.attr.get("FmtPerLine");
		if(aval != null) {
			if(aval.startsWith("0x")) def.perLine = Integer.parseUnsignedInt(aval.substring(2), 16);
			else def.perLine = Integer.parseUnsignedInt(aval);
		}
		
		int sizebits = 0;
		for(LiteNode child : node.children) {
			if(child.name == null) continue;
			StructMember mem = new StructMember();
			mem.name = child.attr.get("Name");
			mem.typeName = child.attr.get("Type");
			aval = child.attr.get("Size");
			if(aval != null) {
				if(aval.startsWith("0x")) mem.size = Integer.parseUnsignedInt(aval.substring(2), 16);
				else mem.size = Integer.parseUnsignedInt(aval);
			}
			else mem.size = 4;
			
			if(child.name.equals("Field")) {
				mem.memType = MEMBER_TYPE_FIELD;
				sizebits += mem.size << 3;
			}
			else if(child.name.equals("Bitfield")) {
				mem.memType = MEMBER_TYPE_BITFIELD;
				sizebits += mem.size;
			}
			else continue;
			
			def.members.add(mem);
		}
		
		if(!sizeDefined) {
			def.size = (sizebits + 0x7) >>> 3;
		}
		
		return def;
	}

	public String toC(byte[] data) {
		long val = 0L;
		int by_pos = 0;
		int bi_pos = 0;
		int shamt = 0;
		String str = "{";
		boolean first = true;
		for(StructMember mem : members) {
			if(!first) str += ", ";
			else first = false;
			
			int hexdig = mem.getHexDigits();
			String fmtstr = "0x%0" + hexdig + "x";
			val = 0L;
			shamt = 0;
			if(mem.memType == MEMBER_TYPE_FIELD) {
				if(bi_pos != 0) {
					by_pos++;
					bi_pos = 0;
				}
				for(int i = 0; i < mem.size; i++) {
					int b = Byte.toUnsignedInt(data[by_pos++]);
					val |= (b << shamt);
					shamt += 8;
				}
			}
			else if(mem.memType == MEMBER_TYPE_BITFIELD) {
				int pos = 0;
				while(pos < mem.size) {
					int b = Byte.toUnsignedInt(data[by_pos]);
					b >>= bi_pos++;
					b &= 1;
					val |= (b << pos);
					if(bi_pos >= 8) {
						bi_pos = 0;
						by_pos++;
					}
					pos++;
				}
			}
			str += String.format(fmtstr, val);
		}
		str += "}";
		return str;
	}
	
}
