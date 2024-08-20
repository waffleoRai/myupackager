package waffleoRai_extractMyu.psyq;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.BufferReference;

public class PsyqItem {
	
	public static final String FIELDKEY_SIZE = "Size";
	public static final String FIELDKEY_SECTION = "SecId";
	public static final String FIELDKEY_SYMBOL = "SymId";
	public static final String FIELDKEY_OFFSET = "Offset";
	public static final String FIELDKEY_RELOCTYPE = "RelocType";
	public static final String FIELDKEY_GROUP = "Group";
	public static final String FIELDKEY_ALIGN = "Align";
	public static final String FIELDKEY_FILEID = "FileId";
	public static final String FIELDKEY_PROGTYPE = "ProgType";
	public static final String FIELDKEY_BYTE = "Byte";
	public static final String FIELDKEY_WORD = "Word";
	public static final String FIELDKEY_LINENUM = "LineNo";
	public static final String FIELDKEY_FRAME_REG = "FrameReg";
	public static final String FIELDKEY_FRAME_SIZE = "FrameSize";
	public static final String FIELDKEY_RA = "RetReg";
	public static final String FIELDKEY_MASK = "Mask";
	public static final String FIELDKEY_MASKOFS = "MaskOffset";
	public static final String FIELDKEY_START = "Start";
	public static final String FIELDKEY_END = "End";
	public static final String FIELDKEY_VALUE = "Value";
	public static final String FIELDKEY_CLASS = "Class";
	public static final String FIELDKEY_TYPE = "Type";
	
	private int command;
	private long filePos;
	
	private Map<String, Integer> values;
	private PsyqExpr expression;
	private String name;
	private String tag;
	private byte[] data;
	
	private int[] dims;
	
	public PsyqItem() {
		values = new HashMap<String, Integer>();
	}
	
	public int getCommand() {return command;}
	public PsyqExpr getExpression() {return expression;}
	public String getName() {return name;}
	public String getTag() {return tag;}
	public byte[] getData() {return data;}
	public long getFilePos() {return filePos;}
	
	public int getValue(String key) {
		Integer val = values.get(key);
		if(val == null) return 0;
		return val;
	}

	public static String readString(BufferReference input) {
		if(input == null) return null;
		int strlen = Byte.toUnsignedInt(input.nextByte());
		String str = input.nextASCIIString(strlen);
		return str;
	}
	
	public static PsyqItem parse(BufferReference input) {
		if(input == null) return null;
		
		int size = 0;
		PsyqItem item = new PsyqItem();
		item.filePos = input.getBufferPosition();
		item.command = Byte.toUnsignedInt(input.nextByte());
		switch(item.command) {
		case PsyqObjCmd.END: break;
		case PsyqObjCmd.BYTES: 
			size = Short.toUnsignedInt(input.nextShort());
			item.values.put(FIELDKEY_SIZE, size);
			item.data = new byte[size];
			for(int i = 0; i < size; i++) item.data[i] = input.nextByte();
			break;
		case PsyqObjCmd.SWITCH: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			break;
		case PsyqObjCmd.ZEROES: 
			item.values.put(FIELDKEY_SIZE, input.nextInt());
			break;
		case PsyqObjCmd.RELOC: 
			item.values.put(FIELDKEY_RELOCTYPE, Byte.toUnsignedInt(input.nextByte()));
			item.values.put(FIELDKEY_OFFSET, Short.toUnsignedInt(input.nextShort()));
			item.expression = PsyqExpr.parse(input);
			break;
		case PsyqObjCmd.EXPORT: 
			item.values.put(FIELDKEY_SYMBOL, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.name = readString(input);
			break;
		case PsyqObjCmd.IMPORT: 
			item.values.put(FIELDKEY_SYMBOL, Short.toUnsignedInt(input.nextShort()));
			item.name = readString(input);
			break;
		case PsyqObjCmd.SECTION: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_GROUP, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_ALIGN, Byte.toUnsignedInt(input.nextByte()));
			item.name = readString(input);
			break;
		case PsyqObjCmd.LOCAL_SYM: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.name = readString(input);
			break;
		case PsyqObjCmd.FILE_NAME: 
			item.values.put(FIELDKEY_FILEID, Short.toUnsignedInt(input.nextShort()));
			item.name = readString(input);
			break;
		case PsyqObjCmd.PROG_TYPE: 
			item.values.put(FIELDKEY_PROGTYPE, Byte.toUnsignedInt(input.nextByte()));
			break;
		case PsyqObjCmd.UNINIT: 
			item.values.put(FIELDKEY_SYMBOL, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_SIZE, input.nextInt());
			item.name = readString(input);
			break;
		case PsyqObjCmd.INC_SLD_LINENUM: 
			item.values.put(FIELDKEY_OFFSET, Short.toUnsignedInt(input.nextShort()));
			break;
		case PsyqObjCmd.INC_SLD_LINENUM_BY_BYTE: 
			item.values.put(FIELDKEY_OFFSET, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_BYTE, Byte.toUnsignedInt(input.nextByte()));
			break;
		case PsyqObjCmd.INC_SLD_LINENUM_BY_WORD: 
			item.values.put(FIELDKEY_OFFSET, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_WORD, Short.toUnsignedInt(input.nextShort()));
			break;
		case PsyqObjCmd.SET_SLD_LINENUM: 
			item.values.put(FIELDKEY_OFFSET, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_LINENUM, input.nextInt());
			break;
		case PsyqObjCmd.SET_SLD_LINENUM_FILE: 
			item.values.put(FIELDKEY_OFFSET, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_LINENUM, input.nextInt());
			item.values.put(FIELDKEY_FILEID, Short.toUnsignedInt(input.nextShort()));
			break;
		case PsyqObjCmd.END_SLD: 
			input.add(2L);
			break;
		case PsyqObjCmd.FUNCTION: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.values.put(FIELDKEY_FILEID, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_LINENUM, input.nextInt());
			item.values.put(FIELDKEY_FRAME_REG, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_FRAME_SIZE, input.nextInt());
			item.values.put(FIELDKEY_RA, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_MASK, input.nextInt());
			item.values.put(FIELDKEY_MASKOFS, input.nextInt());
			item.name = readString(input);
			break;
		case PsyqObjCmd.FUNCTION_END: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.values.put(FIELDKEY_LINENUM, input.nextInt());
			break;
		case PsyqObjCmd.BLOCK_START: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.values.put(FIELDKEY_START, input.nextInt());
			break;
		case PsyqObjCmd.BLOCK_END: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.values.put(FIELDKEY_END, input.nextInt());
			break;
		case PsyqObjCmd.SEC_DEF: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_VALUE, input.nextInt());
			item.values.put(FIELDKEY_CLASS, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_TYPE, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_SIZE, input.nextInt());
			item.name = readString(input);
			break;
		case PsyqObjCmd.SEC_DEF2: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_VALUE, input.nextInt());
			item.values.put(FIELDKEY_CLASS, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_TYPE, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_SIZE, input.nextInt());
			size = Short.toUnsignedInt(input.nextShort());
			if(size > 0) {
				item.dims = new int[size];
				for(int i = 0; i < size; i++) item.dims[i] = Short.toUnsignedInt(input.nextShort());
			}
			item.tag = readString(input);
			item.name = readString(input);
			break;
		case PsyqObjCmd.FUNC_START_2: 
			item.values.put(FIELDKEY_SECTION, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_OFFSET, input.nextInt());
			item.values.put(FIELDKEY_FILEID, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_LINENUM, input.nextInt());
			item.values.put(FIELDKEY_FRAME_REG, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_FRAME_SIZE, input.nextInt());
			item.values.put(FIELDKEY_RA, Short.toUnsignedInt(input.nextShort()));
			item.values.put(FIELDKEY_MASK, input.nextInt());
			item.values.put(FIELDKEY_MASKOFS, input.nextInt());
			item.values.put("Unk1", input.nextInt());
			item.values.put("Unk2", input.nextInt());
			item.name = readString(input);
			break;
		default: return null;
		}
		
		return item;
	}
	
	public void writeToXML(Writer writer, String indent) throws IOException {
		if(indent == null) indent = "";
		
		writer.write(indent + "<Command");
		writer.write(String.format(" Op=\"%s\"", PsyqObjCmd.cmd2String(command)));
		writer.write(String.format(" Pos=\"0x%x\"", filePos));
		if(name != null) writer.write(String.format(" Name=\"%s\"", name));
		if(tag != null) writer.write(String.format(" Tag=\"%s\"", tag));
		
		List<String> keylist = new ArrayList<String>(values.size() + 1);
		keylist.addAll(values.keySet());
		Collections.sort(keylist);
		
		for(String key : keylist) {
			if(key.equals(FIELDKEY_RELOCTYPE)) {
				int rt = values.get(key);
				writer.write(String.format(" %s=\"%s\"", key, PsyqObjCmd.reloc2String(rt)));
			}
			else {
				writer.write(String.format(" %s=\"0x%x\"", key, values.get(key)));
			}
		}
		
		if(expression != null) {
			writer.write(">\n");
			expression.writeToXML(writer, indent + "\t");
			writer.write(indent + "</Command>\n");
		}
		else writer.write("/>\n");
		
	}

}
