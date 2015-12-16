import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.SortedSet;
import java.io.*;


public class Server {
	
	private static int listener_port = 8765;
	private static int n_blocks = 128;
	private static int blocksize = 4096;
	private static Memory mem;
	
	private static int isPositiveInteger(String s) {
		int num;
		try {
			num = Integer.parseInt(s);
			if (num < 0) {
				return -1;
			}
		} catch (NumberFormatException e) {
			return -1;
		}
		return num;
	}
	
	private synchronized static void store(String filename, byte[] file_contents, DataOutputStream toClient) throws IOException {
		
		//Check to see if the file already exists
		if (mem.containsFile(filename)) {
			byte[] file_exists_error = new String("ERROR: FILE EXISTS\n").getBytes();
			toClient.write(file_exists_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: FILE EXISTS");
			return;
		}
		
		//Check if there is space to store the file
		int num_blocks = (int) Math.ceil((double)file_contents.length / blocksize);
		if (!mem.hasSpace(num_blocks)) {
			byte[] no_memory_error = new String("ERROR: NOT ENOUGH MEMORY AVAILABE\n").getBytes();
			toClient.write(no_memory_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: NOT ENOUGH MEMORY AVAILABE");
			return;
		}
		
		try {
			//Write the contents to the file
			FileOutputStream file = new FileOutputStream(".storage/" + filename);
			file.write(file_contents);
			file.close();
			
			//Add the file to memory
			int num_clusters = mem.addFile(filename, num_blocks);
			
			//Output what was stored
			System.out.print("[thread " +Thread.currentThread().getId() + "] Stored file '" + mem.getFileChar(filename) + "' (" + file_contents.length + " bytes; ");
			if (num_blocks > 1) {
				System.out.print(num_blocks + " blocks; ");
			} else {
				System.out.print(num_blocks + " block; ");
			}
			if (num_clusters > 1) {
				System.out.print(num_clusters + " clusters)\n");
			} else {
				System.out.print(num_clusters + " cluster)\n");
			}
			mem.printMemory();
			
			//Send an acknowledgement
			byte[] ack = new String("ACK\n").getBytes();
			toClient.write(ack);
			System.out.println("Sent: ACK");
			
		//Handle exceptions
		} catch (FileNotFoundException e) {
			byte[] file_create_error = new String("ERROR: COULD NOT CREATE FILE '" + filename + "'\n").getBytes();
			toClient.write(file_create_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: COULD NOT CREATE FILE '" + filename + "'");
		} catch (IOException e) {
			byte[] file_write_error = new String("ERROR: COULD NOT WRITE TO FILE '" + filename + "'\n").getBytes();
			toClient.write(file_write_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: COULD NOT WRITE TO FILE '" + filename + "'");
		}
	}
	
	private synchronized static void read(String filename, int byte_offset, int length, DataOutputStream toClient) throws IOException {
		
		try {
			FileInputStream file = new FileInputStream(".storage/" + filename);
			long file_size = new File(".storage/" + filename).length();
			if (byte_offset+length > file_size) {
				byte[] range_error = new String("ERROR: INVALID BYTE RANGE\n").getBytes();
				toClient.write(range_error);
				System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: INVALID BYTE RANGE");
				file.close();
				return;
			}
			byte[] buffer = new byte[(int) file_size];
			
			int bytes_read = file.read(buffer);
			
			
			if (bytes_read == -1) {
				byte[] range_error = new String("ERROR: INVALID BYTE RANGE\n").getBytes();
				toClient.write(range_error);
				System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: INVALID BYTE RANGE");
			} else {
				byte[] ack = new String("ACK " + bytes_read + "\n").getBytes();
				toClient.write(ack);
				toClient.write(buffer, byte_offset, length);
				System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ACK " + bytes_read);
				int num_blocks_read = mem.readFile(byte_offset, bytes_read);
				System.out.print("[thread " +Thread.currentThread().getId() + "] Sent " + bytes_read + " bytes (from " + num_blocks_read + " '" + mem.getFileChar(filename));
				if (num_blocks_read > 1) {
					System.out.print("' blocks) from offset " + byte_offset + "\n");
				} else {
					System.out.print("' block) from offset " + byte_offset + "\n");
				}
			}
			
			file.close();
			
		} catch (FileNotFoundException e) {
			byte[] no_file_error = new String("ERROR: NO SUCH FILE\n").getBytes();
			toClient.write(no_file_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: NO SUCH FILE");
		} catch (IOException e) {
			byte[] read_file_error = new String("ERROR: COULD NOT READ FROM FILE '" + filename + "'\n").getBytes();
			toClient.write(read_file_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: COULD NOT READ FROM FILE '" + filename + "'");
		}
		
		
	}
	
	private synchronized static void delete(String filename, DataOutputStream toClient) throws IOException {
		if (filename == "*") {
			return;
		}
		Path path = Paths.get(".storage/" + filename);
		try {
			Files.delete(path);
			Character file_char = mem.getFileChar(filename);
			int deallocated_blocks = mem.removeFile(filename);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Deleted " + filename + " file '" + file_char + "' (deallocated " + deallocated_blocks + " blocks)");
			mem.printMemory();
			byte[] ack = new String("ACK\n").getBytes();
			toClient.write(ack);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ACK");
		} catch (IOException e) {
			byte[] file_error = new String("ERROR: NO SUCH FILE\n").getBytes();
			toClient.write(file_error);
			System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: NO SUCH FILE");
		}
	}
	
	private synchronized static void dir(DataOutputStream toClient) throws IOException {
		SortedSet<String> set = mem.getFiles();
		String dir = new String(set.size() + "\n");
		Iterator<String> itr = set.iterator();
		while (itr.hasNext()) {
			dir += itr.next() + "\n";
		}
		toClient.write(dir.getBytes());
		System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: " + dir);
	}
	class SocketRunnable implements Runnable {
		protected Socket socket = null;
	
		private DataInputStream fromClient;
		private DataOutputStream toClient;
	
		public SocketRunnable(Socket clientSocket){
			this.socket = clientSocket;
		}

		public void run() {
			try{
				fromClient = new DataInputStream(new BufferedInputStream(socket.getInputStream()));			
				toClient = new DataOutputStream(socket.getOutputStream());
				
				
				
				boolean client_terminated = false;
				while (!client_terminated) {
					byte[] b = new byte[n_blocks * blocksize];
					int bytes_read = fromClient.read(b);	
					
					if (bytes_read == -1) {
						System.out.println("[thread " +Thread.currentThread().getId() + "] Client closed its socket....terminating");
						break;
					}
					
					String line = new String(b, "UTF-8");	
					String argument_line = line.trim();
					String data_line = "";
					for (int i = 0; i < argument_line.length(); i++) {
						if (argument_line.charAt(i) == '\n') {
							argument_line = argument_line.substring(0, i);
							data_line = line.substring(i+1);
							data_line = data_line.trim();
							break;
						}
					}				
					
					if (argument_line.equals("")) {
						continue;
					}
					
					System.out.println("[thread " +Thread.currentThread().getId() + "] Rcvd: " + argument_line);
					String[] arguments = argument_line.split(" ");
					String instruction = arguments[0];
							
					if (instruction.equals("STORE")) {
						if (arguments.length != 3) {
							byte[] store_error = new String("ERROR: STORE command must be in the form 'STORE <filename> <bytes>'\n").getBytes();
							toClient.write(store_error);
							System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: STORE command must be in the form 'STORE <filename> <bytes>'");
						} else {
							String filename = arguments[1];
							int num_bytes = isPositiveInteger(arguments[2]);
							if (num_bytes == -1) {
								byte[] int_error = new String("ERROR: The <bytes> argument must be a positive integer\n").getBytes();
								toClient.write(int_error);
								System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: The <bytes> argument must be a positive integer");
							} else {
								byte[] file_contents = new byte[num_bytes];
								int current_num_bytes = 0;
								if (data_line.isEmpty()) {
									while(current_num_bytes < num_bytes) {
										//System.out.println("current_num_bytes = " + current_num_bytes);
										int tmp = fromClient.read(file_contents, current_num_bytes, num_bytes-current_num_bytes);
										if (tmp == -1) {
											System.out.println("[thread " +Thread.currentThread().getId() + "] Client closed its socket....terminating");
											client_terminated = true;
											break;
										}
										//System.out.println("tmp = " + tmp);
										current_num_bytes += tmp;
									}
								} else {
									file_contents = data_line.getBytes();
								}
								
								if (!client_terminated) {
									store(filename, file_contents, toClient);
								}
							}
							
						}
						continue;
						
						
					} else if (instruction.equals("READ")) {
						if (arguments.length != 4) {
							byte[] read_error = new String("ERROR: READ command must be in the form 'READ <filename> <byte_offset> <length>'\n").getBytes();
							toClient.write(read_error);
							System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: READ command must be in the form 'READ <filename> <byte_offset> <length>'");
						} else {
							String filename = arguments[1];
							int byte_offset = isPositiveInteger(arguments[2]);
							int length = isPositiveInteger(arguments[3]);
							if (byte_offset < 0 || length < 0) {
								byte[] read_length_error = new String("ERROR: Byte-offset and length need to be positive integers\n").getBytes();
								toClient.write(read_length_error);
								System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: Byte-offset and length need to be positive integers");
							} else {
								read(filename, byte_offset, length, toClient);
							}
							
						}
						
						
						
					} else if (instruction.equals("DELETE")) {
						if (arguments.length != 2) {
							byte[] delete_error = new String("ERROR: DELETE command must be in the form 'DELETE <filename>'\n").getBytes();
							toClient.write(delete_error);
							System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: DELETE command must be in the form 'DELETE <filename>'");
						} else {
							String filename = arguments[1];
							delete(filename, toClient);
						}
						
						
						
					} else if (instruction.equals("DIR")) {
						if (arguments.length != 1) {
							byte[] dir_error = new String("ERROR: DIR command must be in the form 'DIR'\n").getBytes();
							toClient.write(dir_error);
							System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: DIR command must be in the form 'DIR'");
						} else {
							dir(toClient);
						}
						
						
						
					} else {
						byte[] invalid_first_arg_error = new String("ERROR: First argument must be STORE, READ, DELETE, or DIR\n").getBytes();
						toClient.write(invalid_first_arg_error);
						System.out.println("[thread " +Thread.currentThread().getId() + "] Sent: ERROR: First argument must be STORE, READ, DELETE, or DIR");
					}
					
				}
						
			} catch (IOException e) {		
				System.out.println("ERROR: Could not write back to client");
			} finally {
				try {
					fromClient.close();
					toClient.close();
				} catch (IOException e) {
					System.out.println("[thread " +Thread.currentThread().getId() + "] ERROR: Could not close connection to client");
				}
			}

		}	
	}
	
	public static void main(String[] args) {
		Server server = new Server();
		server.start();
	}
	public void start(){		
		System.out.println("Block size is " + blocksize);
		System.out.println("Number of blocks is " + n_blocks);
		
		mem = new Memory(n_blocks, blocksize);
		mem.printMemory();
		try{
			File storage_dir;
			Path storage_path = Paths.get(".storage");
			if (Files.exists(storage_path)) {
				storage_dir = storage_path.toFile();
				String[] storage_files = storage_dir.list();
				for (String s : storage_files) {
					File currentFile = new File(storage_dir.getPath(), s);
					currentFile.delete();
				}
				Files.delete(storage_path);
			}
		
		storage_dir = new File(".storage");
		storage_dir.mkdir();
		}catch(IOException e){
			System.out.println("Filesystem Unavailable");
			return;
		}	ServerSocket serverSocket = null;
		try{	
			serverSocket = new ServerSocket(listener_port);
		}catch(IOException e){
			return;
		}
		System.out.println("Listening on port " + listener_port);
		while(true){
				Socket socket = null;
				try{
					socket = serverSocket.accept();
					System.out.println("Received incoming connection from " + socket.getInetAddress().getHostName());
					new Thread(new SocketRunnable(socket)).start();	
				}catch(IOException e){
					e.printStackTrace();
				}
		}
	}
}
