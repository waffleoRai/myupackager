package waffleoRai_extractMyu.psyq;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PsyqSection {
	
	private String name;
	private int id;
	private int size;
	private long objOfs;
	
	private List<PsyqItem> symbols;
	
	public PsyqSection() {
		symbols = new LinkedList<PsyqItem>();
	}
	
	public String getName() {return name;}
	public int getID() {return id;}
	public int getSize() {return size;}
	public long getOffset() {return objOfs;}
	
	public List<PsyqItem> getSymbols(){
		List<PsyqItem> copy = new ArrayList<PsyqItem>(symbols.size());
		copy.addAll(symbols);
		return copy;
	}
	
	public void setName(String val) {name = val;}
	public void setId(int val) {id = val;}
	public void setSize(int val) {size = val;}
	public void setOffset(long val) {objOfs = val;}
	
	public void clearSymbols() {
		symbols.clear();
	}
	
	public void addSymbol(PsyqItem sym) {
		symbols.add(sym);
	}

}
