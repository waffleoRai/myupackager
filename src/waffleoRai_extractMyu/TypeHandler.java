package waffleoRai_extractMyu;

public interface TypeHandler {
	
	public boolean exportCallback(ExportContext ctx);
	
	public static TypeHandler getHandlerFor(String type){
		//Default to returning the "unknown file type" handler
		TypeHandler handler = MyuArcCommon.getTypeHandler(type);
		if(handler == null){
			handler = new MyuUnkTypeHandler();
		}
		return handler;
	}

}
