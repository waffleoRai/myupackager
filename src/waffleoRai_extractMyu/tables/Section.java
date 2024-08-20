package waffleoRai_extractMyu.tables;

public class Section implements Comparable<Section>{
	
	private boolean isSys;
	private String library;
	private String name;
	
	private long textAddr;
	private long rodataAddr;
	private long dataAddr;
	private long bssAddr;
	private long sdataAddr;
	private long sbssAddr;
	
	private int textSize;
	private int rodataSize;
	private int dataSize;
	private int sdataSize;
	private int bssSize;
	private int sbssSize;
	
	/*----- Getters -----*/
	
	public boolean isSys() {return isSys;}
	public String getLibName() {return library;}
	public String getName() {return name;}
	public long getTextAddr() {return textAddr;}
	public long getRODataAddr() {return rodataAddr;}
	public long getDataAddr() {return dataAddr;}
	public long getBssAddr() {return bssAddr;}
	public long getSDataAddr() {return sdataAddr;}
	public long getSBssAddr() {return sbssAddr;}
	
	public int getTextSize() {return textSize;}
	public int getDataSize() {return dataSize;}
	public int getRODataSize() {return rodataSize;}
	public int getSDataSize() {return sdataSize;}
	public int getBssSize() {return bssSize;}
	public int getSBssSize() {return sbssSize;}
	
	public boolean hasText() {return textAddr > 0L;}
	public boolean hasROData() {return rodataAddr > 0L;}
	public boolean hasData() {return dataAddr > 0L;}
	public boolean hasBss() {return bssAddr > 0L;}
	public boolean hasSData() {return sdataAddr > 0L;}
	public boolean hasSBss() {return sbssAddr > 0L;}
	
	/*----- Setters -----*/
	
	public void setIsSys(boolean val) {isSys = val;}
	public void setLibName(String val) {library = val;}
	public void setName(String val) {name = val;}
	public void setTextAddr(long val) {textAddr = val;}
	public void setRODataAddr(long val) {rodataAddr = val;}
	public void setDataAddr(long val) {dataAddr = val;}
	public void setBssAddr(long val) {bssAddr = val;}
	public void setSDataAddr(long val) {sdataAddr = val;}
	public void setSBssAddr(long val) {sbssAddr = val;}
	
	public void setTextSize(int val) {textSize = val;}
	public void setRODataSize(int val) {rodataSize = val;}
	public void setDataSize(int val) {dataSize = val;}
	public void setSDataSize(int val) {sdataSize = val;}
	public void setBssSize(int val) {bssSize = val;}
	public void setSBssSize(int val) {sbssSize = val;}
	
	/*----- Setters -----*/
	
	public int compareTo(Section o) {
		if(o == null) return 1;
		if(textAddr > 0L && o.textAddr > 0L) {
			return (int)(this.textAddr - o.textAddr);
		}
		if(dataAddr > 0L && o.dataAddr > 0L) {
			return (int)(this.dataAddr - o.dataAddr);
		}
		if(bssAddr > 0L && o.bssAddr > 0L) {
			return (int)(this.bssAddr - o.bssAddr);
		}
		return 0;
	}

}
