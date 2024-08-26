package waffleoRai_extractMyu.tables;

public class SymbolInfo implements Comparable<SymbolInfo>{
	
	public String name;
	public long address;
	public String type;
	public int sizeBytes;
	
	public int hashCode() {
		if(name != null) return (int)address ^ name.hashCode();
		return (int)address;
	}
	
	public boolean equals(Object o) {
		if(o == null) return false;
		if(o == this) return true;
		if(!(o instanceof SymbolInfo)) return false;
		
		SymbolInfo other = (SymbolInfo)o;
		if(other.address != this.address) return false;
		if(other.sizeBytes != this.sizeBytes) return false;
		if(this.name != null) {
			if(!this.name.equals(other.name)) return false;
		}
		else if(other.name != null) return false;
		if(this.type != null) {
			if(!this.type.equals(other.type)) return false;
		}
		else if(other.type != null) return false;
		
		return true;
	}

	public int compareTo(SymbolInfo o) {
		if(o == null) return 1;
		if(this.address != o.address) {
			if(this.address > o.address) return 1;
			return -1;
		}
		
		if(this.name != null) {
			return this.name.compareTo(o.name);
		}
		else {
			if(o.name != null) return 1;
		}
		
		return 0;
	}

}
