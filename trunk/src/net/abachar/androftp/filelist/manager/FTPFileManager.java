package net.abachar.androftp.filelist.manager;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;

import net.abachar.androftp.servers.Logontype;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;

import android.content.Context;
import android.os.Bundle;

public class FTPFileManager extends AbstractFileManager {

	/**
	 * 
	 */
	private FTPClient ftpClient;

	/**
	 * Server configuration
	 */
	private String host;
	private int port;
	private Logontype logontype;
	private String username;
	private String password;

	/**
	 * Default constructor
	 */
	public FTPFileManager(Context context) {
		super(context);
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.FileManager#init(android.os.Bundle)
	 */
	@Override
	public void init(Bundle bundle) {

		// Initial order
		if (bundle.containsKey("server.orderBy")) {
			mOrderByComparator = new OrderByComparator((OrderBy) bundle.get("server.orderBy"));
		}

		// Server configuration
		host = bundle.getString("server.host");
		port = bundle.getInt("server.port");
		logontype = (Logontype) bundle.get("server.logontype");
		if (logontype == Logontype.NORMAL) {
			username = bundle.getString("server.username");
			password = bundle.getString("server.password");
		} else {
			username = null;
			password = null;
		}

		// Not connected
		mRootPath = mCurrentPath = "";
		mInRootFolder = true;
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.FileManager#doConnect()
	 */
	protected void doConnect() throws FileManagerException {

		// Connect
		try {

			// New ftp client
			ftpClient = new FTPClient();

			// Connect to server
			ftpClient.connect(host, port);

			// Check the reply code to verify success.
			int reply = ftpClient.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				throw new ConnectionException("E0121");
			}

			if (logontype == Logontype.NORMAL) {
				if (!ftpClient.login(username, password)) {
					ftpClient.logout();
					throw new ConnectionException("E0122");
				}
			}

			mConnected = true;

			ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			ftpClient.enterLocalPassiveMode();

			mRootPath = mCurrentPath = ftpClient.printWorkingDirectory();

			// Load files
			loadFiles();

		} catch (SocketException e) {
			mConnected = false;
			throw new ConnectionException("E0101");
		} catch (IOException e) {
			mConnected = false;
			throw new ConnectionException("E0102");
		} catch (ConnectionException e) {
			mConnected = false;
			try {
				ftpClient.disconnect();
			} catch (IOException e1) {
			}

			throw e;
		}
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.FileManager#doChangeToParentDirectory()
	 */
	@Override
	protected void doChangeToParentDirectory() throws FileManagerException {

		try {
			if (ftpClient.changeToParentDirectory()) {
				mCurrentPath = ftpClient.printWorkingDirectory();

				// refresh list files
				mInRootFolder = mRootPath.equals(mCurrentPath);
				loadFiles();
			}
		} catch (FTPConnectionClosedException e) {
			throw new ConnectionException("E0151", e);
		} catch (IOException e) {
			throw new FileManagerException("E0161", e);
		}
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.FileManager#doChangeWorkingDirectory(net.abachar.androftp.filelist.manager.FileEntry)
	 */
	@Override
	protected void doChangeWorkingDirectory(FileEntry dir) throws FileManagerException {

		try {
			if (ftpClient.changeWorkingDirectory(dir.getAbsolutePath())) {
				mCurrentPath = ftpClient.printWorkingDirectory();

				// refresh list files
				mInRootFolder = mRootPath.equals(mCurrentPath);
				loadFiles();
			}
		} catch (FTPConnectionClosedException e) {
			throw new ConnectionException("E0151", e);
		} catch (IOException e) {
			throw new FileManagerException("E0161", e);
		}
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.AbstractFileManager#doDeleteFiles(net.abachar.androftp.filelist.manager.FileEntry[])
	 */
	@Override
	protected void doDeleteFiles(FileEntry[] files) throws FileManagerException {

		try {
			boolean ret;
			for (FileEntry file : files) {

				if (file.isFolder()) {
					ret = ftpClient.removeDirectory(file.getName());
				} else {
					ret = ftpClient.deleteFile(file.getName());
				}

				if (!ret) {
					// Toast.makeText(mContext, R.string.err_delete_file,
					// Toast.LENGTH_SHORT).show(); Exception
				}
			}

			// Refresh file list
			loadFiles();

		} catch (FTPConnectionClosedException e) {
			throw new ConnectionException("E0151", e);
		} catch (IOException e) {
			throw new FileManagerException("E0161", e);
		}
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.AbstractFileManager#doCreateNewfolder(net.abachar.androftp.filelist.manager.FileEntry)
	 */
	@Override
	protected void doCreateNewfolder(FileEntry dir) throws FileManagerException {

		try {

			// Make directory
			if (ftpClient.makeDirectory(dir.getName())) {
				// Refresh file list
				loadFiles();
			} else {
				// R.string.err_create_folder, Exception
			}

		} catch (FTPConnectionClosedException e) {
			throw new ConnectionException("E0151", e);
		} catch (IOException e) {
			// Toast.makeText(mContext, R.string.err_rename_file,
			// Toast.LENGTH_SHORT).show(); Exception
			throw new FileManagerException("E0161", e);
		}
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.AbstractFileManager#doRefresh()
	 */
	@Override
	protected void doRefresh() throws FileManagerException {

		try {
			// Refresh file list
			loadFiles();

		} catch (FTPConnectionClosedException e) {
			throw new ConnectionException("E0151", e);
		} catch (IOException e) {
			throw new FileManagerException("E0161", e);
		}
	}

	/**
	 * @see net.abachar.androftp.filelist.manager.AbstractFileManager#doRenameFile(net.abachar.androftp.filelist.manager.FileEntry,
	 *      net.abachar.androftp.filelist.manager.FileEntry)
	 */
	@Override
	protected void doRenameFile(FileEntry file, FileEntry newFile) throws FileManagerException {

		try {

			if (ftpClient.rename(file.getName(), newFile.getName())) {
				// Refresh file list
				loadFiles();
			} else {
				// R.string.err_rename_file, Exception
			}

		} catch (FTPConnectionClosedException e) {
			throw new ConnectionException("E0151", e);
		} catch (IOException e) {
			throw new FileManagerException("E0161", e);
		}

	}

	/**
	 * @throws IOException
	 * 
	 */
	private void loadFiles() throws IOException {
		mFileList = null;

		// Load server files
		FTPFile[] list = ftpClient.listFiles(mCurrentPath, new FTPFileFilter() {
			@Override
			public boolean accept(FTPFile file) {
				String fileName = file.getName();

				if (file.isDirectory()) {
					return !".".equals(fileName) && !"..".equals(fileName);
				}

				return !file.isSymbolicLink();
			}
		});

		// Scan all files
		if ((list != null) && (list.length > 0)) {
			mFileList = new ArrayList<FileEntry>();
			for (FTPFile sf : list) {
				FileEntry df = new FileEntry();
				df.setName(sf.getName());
				df.setAbsolutePath(mCurrentPath + File.separator + sf.getName());
				df.setParentPath(mCurrentPath);
				df.setSize(sf.getSize());
				df.setType(FileType.fromFTPFile(sf));
				df.setLastModified(sf.getTimestamp().getTimeInMillis());

				mFileList.add(df);
			}

			// Sort
			Collections.sort(mFileList, mOrderByComparator);
		}
	}
}
