package waffleoRai_myuctempl;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MCTSymbol {
	
	public static final int SYMBOL_TYPE_UNK = -1;
	public static final int SYMBOL_TYPE_TEXT = 0;
	public static final int SYMBOL_TYPE_RODATA = 1;
	public static final int SYMBOL_TYPE_DATA = 2;
	public static final int SYMBOL_TYPE_BSS = 3;

	public String name;
	public long address = 0L; //If known
	public int section = SYMBOL_TYPE_UNK;
	public Set<String> dependencies;
	public boolean isPublic = false;
	public List<String> asmLines; //Just load into mem and dump back out as-is to func asm files.
	public Set<String> blabels;
	public Set<String> jlabels;
	
	public List<MCTSymbol> exclusiveRodata; //local .rodata used only by this function
	
	public MCTSymbol() {
		dependencies = new HashSet<String>();
		asmLines = new LinkedList<String>();
		blabels = new HashSet<String>();
		jlabels = new HashSet<String>();
		exclusiveRodata = new LinkedList<MCTSymbol>();
	}
	
	public void tidy() {
		dependencies.removeAll(blabels);
		dependencies.removeAll(jlabels);
		
		List<MCTSymbol> keeplist = new LinkedList<MCTSymbol>();
		for(MCTSymbol ro : exclusiveRodata) {
			if(!ro.isPublic) keeplist.add(ro);
		}
		exclusiveRodata = keeplist;
	}
	
	public int getSize() {
		//TODO
		return 0;
	}
	
	public String guessReturnType() {
		if(section == SYMBOL_TYPE_TEXT) return "void";
		for(String asm : asmLines) {
			if(asm.contains(".byte")) return "uint8_t";
			if(asm.contains(".short")) return "uint16_t";
			if(asm.contains(".word")) {
				if(!asm.contains("0x")) return "void*";
			}
		}
		
		return "uint32_t";
	}
	
	
}
