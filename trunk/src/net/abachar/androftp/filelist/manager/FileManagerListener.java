package net.abachar.androftp.filelist.manager;

/**
 * 
 * @author abachar
 */
public interface FileManagerListener {

	/**
	 * 
	 */
	public void onFileManagerEvent(FileManager fm, FileManagerEvent event);
}