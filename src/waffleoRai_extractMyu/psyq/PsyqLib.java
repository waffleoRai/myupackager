package waffleoRai_extractMyu.psyq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import waffleoRai_Utils.BufferReference;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;

public class PsyqLib {
	
	private int version;
	private List<PsyqObj> contents;
	
	public PsyqLib() {
		contents = new LinkedList<PsyqObj>();
	}
	
	public static PsyqLib parse(BufferReference input) throws IOException, UnsupportedFileTypeException {
		if(input == null) return null;
		PsyqLib lib = new PsyqLib();
		input.setByteOrder(false);
		
		String mcheck = input.nextASCIIString(3);
		if(mcheck == null || !mcheck.equals("LIB")) return null;
		lib.version = Byte.toUnsignedInt(input.nextByte());
		
		while(input.hasRemaining()) {
			String objname = input.nextASCIIString(8);
			objname = objname.trim();
			
			//Skip 4 unknown
			input.add(4L);
			
			//Offset (from very start)
			long prefixSize = Integer.toUnsignedLong(input.nextInt());
			
			//Size
			long size = Integer.toUnsignedLong(input.nextInt());
			
			//Skip what looks like a symbol list
			long symListSize = prefixSize - 20L;
			long lnkDatSize = size - prefixSize;
			
			input.add(symListSize);
			
			long stpos = input.getBufferPosition();
			long edpos = stpos + lnkDatSize;
			
			FileBuffer buffer = input.getBuffer();
			FileBuffer objBuff = buffer.createReadOnlyCopy(stpos, edpos);
			PsyqObj obj = PsyqObj.parse(objBuff.getReferenceAt(0L));
			obj.setName(objname);
			lib.contents.add(obj);
			objBuff.dispose();
			
			input.add(lnkDatSize);
		}
		
		return lib;
	}
	
	public int getVersion() {return version;}
	
	public List<PsyqObj> getContents(){
		List<PsyqObj> copy = new ArrayList<PsyqObj>(contents.size());
		copy.addAll(contents);
		return copy;
	}

}
