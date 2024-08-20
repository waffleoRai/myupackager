package waffleoRai_myuctempl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import waffleoRai_DataContainers.MultiValMap;

public class MCTModule {
	
	public String textPath;
	public String rodataPath;
	public String dataPath;
	public int bssSize;
	public String cPath;
	public String hPath;
	public List<String> upDirs;
	public List<String> downDirs;
	public String sSplitDir;
	
	public String modName;
	
	public List<MCTSymbol> textSymbols;
	public List<MCTSymbol> dataSymbols;
	public List<MCTSymbol> rodataSymbols;
	
	public Map<String, MCTSymbol> symMap;
	public Set<String> allDeps;
	public Set<String> allInternal; //Including subsymbols
	
	public MCTModule() {
		symMap = new HashMap<String, MCTSymbol>();
		allDeps = new HashSet<String>();
		allInternal = new HashSet<String>();
		textSymbols = new LinkedList<MCTSymbol>();
		dataSymbols = new LinkedList<MCTSymbol>();
		rodataSymbols = new LinkedList<MCTSymbol>();
		
		upDirs = new LinkedList<String>();
		downDirs = new LinkedList<String>();
	}
	
	public void addSymbol(MCTSymbol symbol) {
		if(symbol == null) return;
		if(symbol.name == null) return;
		
		symbol.tidy();
		symMap.put(symbol.name, symbol);
		allInternal.add(symbol.name);
		allInternal.addAll(symbol.blabels);
		allInternal.addAll(symbol.jlabels);
		allDeps.addAll(symbol.dependencies);
		
		switch(symbol.section) {
		case MCTSymbol.SYMBOL_TYPE_TEXT:
			textSymbols.add(symbol); break;
		case MCTSymbol.SYMBOL_TYPE_RODATA:
			rodataSymbols.add(symbol); break;
		case MCTSymbol.SYMBOL_TYPE_DATA:
			dataSymbols.add(symbol); break;
		}
		
	}
	
	public void tidy() {
		allDeps.removeAll(allInternal);
	}
	
	public void linkLocalROData() {
		tidy();
		MultiValMap<String, MCTSymbol> refmap = new MultiValMap<String, MCTSymbol>();
		for(MCTSymbol func : textSymbols) {
			func.exclusiveRodata.clear();
			for(String d : func.dependencies) {
				refmap.addValue(d, func);
			}
		}
		
		for(MCTSymbol sym : rodataSymbols) {
			if(sym.isPublic) continue;
			List<MCTSymbol> funcs = refmap.getValues(sym.name);
			if(funcs != null && !funcs.isEmpty()) {
				if(funcs.size() == 1) {
					//Link
					funcs.get(0).exclusiveRodata.add(sym);
				}
			}
		}
	}

}
