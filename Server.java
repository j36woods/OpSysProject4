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
	
	private static void store(DataOutputStream toClient, String filename, byte[] file_contents) throws IOException {
		
		//Check to see if the file already exists
		if (mem.containsFile(filename)) {
			toClient.writeChars("ERROR: FILE EXISTS\n");
			System.out.println("Sent: ERROR: FILE EXISTS");
			return;
		}
		
		//Check if there is space to store the file
		int num_blocks = (int) Math.ceil((double)file_contents.length / blocksize);
		if (!mem.hasSpace(num_blocks)) {
			toClient.writeChars("ERROR: NOT ENOUGH MEMORY AVAILABE\n");
			System.out.println("Sent: ERROR: NOT ENOUGH MEMORY AVAILABE");
			return;
		}
		
		//Fix error messages for throw/catch
		
		try {
			//Write the contents to the file
			FileOutputStream file = new FileOutputStream(".storage/" + filename);
			file.write(file_contents);
			file.close();
			
			//Add the file to memory
			int num_clusters = mem.addFile(filename, num_blocks);
			
			//Output what was stored
			System.out.print("Stored file '" + mem.getFileChar(filename) + "' (" + file_contents.length + " bytes; ");
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
			toClient.writeChars("ACK\n");
			System.out.println("Sent: ACK");
			
		//Handle exceptions
		} catch (FileNotFoundException e) {
			toClient.writeChars("ERROR: COULD NOT CREATE FILE '" + filename + "'\n");
			System.out.println("Sent: ERROR: COULD NOT CREATE FILE '" + filename + "'");
		} catch (IOException e) {
			toClient.writeChars("ERROR: COULD NOT WRITE TO FILE '" + filename + "'\n");
			System.out.println("Sent: ERROR: COULD NOT WRITE TO FILE '" + filename + "'");
		}
	}
	
	private static void read(DataOutputStream toClient, String filename, int byte_offset, int length) throws IOException {
		//Fix error for when the byte_offset is greater than the number of bytes
		
		//Fix error messages for throw/catch
		
		try {
			FileInputStream file = new FileInputStream(".storage/" + filename);
			long file_size = new File(".storage/" + filename).length();
			if (byte_offset+length > file_size) {
				toClient.writeChars("ERROR: INVALID BYTE RANGE\n");
				System.out.println("Sent: ERROR: INVALID BYTE RANGE");
				file.close();
				return;
			}
			byte[] buffer = new byte[(int) file_size];
			
			int bytes_read = file.read(buffer);
			
			
			if (bytes_read == -1) {
				toClient.writeChars("ERROR: INVALID BYTE RANGE\n");
				System.out.println("Sent: ERROR: INVALID BYTE RANGE");
			} else {
				toClient.writeChars("ACK " + bytes_read + "\n");
				toClient.write(buffer, byte_offset, length);
				System.out.println("Sent: ACK " + bytes_read);
				int num_blocks_read = mem.readFile(byte_offset, bytes_read);
				System.out.print("Sent " + bytes_read + " bytes (from " + num_blocks_read + " '" + mem.getFileChar(filename));
				if (num_blocks_read > 1) {
					System.out.print("' blocks) from offset " + byte_offset + "\n");
				} else {
					System.out.print("' block) from offset " + byte_offset + "\n");
				}
			}
			
			file.close();
			
		} catch (FileNotFoundException e) {
			toClient.writeChars("ERROR: NO SUCH FILE\n");
			System.out.println("Sent: ERROR: NO SUCH FILE");
		} catch (IOException e) {
			toClient.writeChars("ERROR: COULD NOT READ FROM FILE '" + filename + "'\n");
			System.out.println("Sent: ERROR: COULD NOT READ FROM FILE '" + filename + "'");
		}
		
		
	}
	
	private static void delete(DataOutputStream toClient, String filename) throws IOException {
		if (filename == "*") {
			return;
		}
		Path path = Paths.get(".storage/" + filename);
		try {
			Files.delete(path);
			
			int deallocated_blocks = mem.removeFile(filename);
			System.out.println("Deleted " + filename + " file '" + mem.getFileChar(filename) + "' (deallocated " + deallocated_blocks + " blocks)");
			mem.printMemory();
			
			toClient.writeChars("ACK\n");
			System.out.println("Sent: ACK");
		} catch (IOException e) {
			toClient.writeChars("ERROR: NO SUCH FILE\n");
			System.out.println("Sent: ERROR: NO SUCH FILE");
		}
	}
	
	private static void dir(DataOutputStream toClient) throws IOException {
		SortedSet<String> set = mem.getFiles();
		Iterator<String> itr = set.iterator();
		while (itr.hasNext()) {
			toClient.writeChars(itr.next() + "\n");
		}
		System.out.println("Sent: Directory");
	}
	
	
	
	public static void main(String[] args) throws IOException {
		
		System.out.println("Block size is " + blocksize);
		System.out.println("Number of blocks is " + n_blocks);
		
		mem = new Memory(n_blocks, blocksize);
		mem.printMemory();
		
		ServerSocket serverSocket = new ServerSocket(listener_port);
		
		System.out.println("Listening on port " + listener_port);

		Socket socket = serverSocket.accept();
		System.out.println("Received incoming connection from " + socket.getInetAddress().getHostName());
		
		DataInputStream fromClient = new DataInputStream(new BufferedInputStream(socket.getInputStream()));			
		DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
		
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
		
		while (!socket.isClosed()) {
				
			try {
				byte[] b = new byte[n_blocks * blocksize];
				int bytes_read = fromClient.read(b);	
				
				String argument_line = new String(b, "UTF-8");	
				argument_line = argument_line.substring(0, bytes_read-2);
				
				if (argument_line.equals("")) {
					continue;
				}
				
				System.out.println("Rcvd: " + argument_line);
				String[] arguments = argument_line.split(" ");
				String instruction = arguments[0];
						
				if (instruction.equals("STORE")) {
					if (arguments.length != 3) {
						toClient.writeChars("ERROR: STORE command must be in the form 'STORE <filename> <bytes>'\n");
						System.out.println("Sent: Invalid STORE command error");
					} else {
						String filename = arguments[1];
						int num_bytes = isPositiveInteger(arguments[2]);
						if (num_bytes == -1) {
							toClient.writeChars("ERROR: The <bytes> argument must be a positive integer\n");
							System.out.println("Sent: Invalid bytes argument error");
						} else {
							byte[] file_contents = new byte[num_bytes];
							fromClient.read(file_contents);
							store(toClient, filename, file_contents);
						}
						
					}
					continue;
					
					
				} else if (instruction.equals("READ")) {
					if (arguments.length != 4) {
						toClient.writeChars("ERROR: READ command must be in the form 'READ <filename> <byte_offset> <length>'\n");
						System.out.println("Sent: Invalid READ command error");
					} else {
						String filename = arguments[1];
						int byte_offset = isPositiveInteger(arguments[2]);
						int length = isPositiveInteger(arguments[3]);
						if (byte_offset < 0 || length < 0) {
							toClient.writeChars("ERROR: Byte-offset and length need to be positive integers\n");
							System.out.println("Sent: Invalid byte-offset or length error");
						} else {
							read(toClient, filename, byte_offset, length);
						}
						
					}
					
					
					
				} else if (instruction.equals("DELETE")) {
					if (arguments.length != 2) {
						toClient.writeChars("ERROR: DELETE command must be in the form 'DELETE <filename>'\n");
						System.out.println("Sent: Invalid DELETE command error");
					} else {
						String filename = arguments[1];
						delete(toClient, filename);
					}
					
					
					
				} else if (instruction.equals("DIR")) {
					if (arguments.length != 1) {
						toClient.writeChars("ERROR: DIR command must be in the form 'DIR'\n");
						System.out.println("Sent: Invalid DIR command error");
					} else {
						dir(toClient);
					}
					
					
					
				} else {
					toClient.writeChars("ERROR: First argument must be STORE, READ, DELETE, or DIR\n");
					System.out.println("Sent: Invalid first argument error");
				}
				
			} catch (IOException e) {
				System.out.println("ERROR: Could not write back to client");
			}			
		}
		
		serverSocket.close();
	}
}
