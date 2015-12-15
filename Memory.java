import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeSet;

public class Memory {
	private int n_blocks;
	private int blocksize;
	private int num_available_blocks;
	private HashMap<String, Character> files;
	private ArrayList<Character> file_chars;
	private ArrayList<Character> memory;
	
	public Memory(int n_blocks_, int blocksize_) {
		n_blocks = n_blocks_;
		blocksize = blocksize_;
		num_available_blocks = n_blocks;
		files = new HashMap<String, Character>();
		file_chars = new ArrayList<>(Arrays.asList('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'));
		memory = new ArrayList<Character>();
		for (int i = 0; i < n_blocks; i++) {
			memory.add('.');
		}
	}
	
	public void printMemory() {
		System.out.println("Simulated Clustered Disk Space Allocation:");
		System.out.println("================================");
		for (int i = 0; i < memory.size(); i++) {
			if (i%(n_blocks/4) == 0 && i != 0) {
				System.out.println();
			}
			System.out.print(memory.get(i));
		}
		
		System.out.println("\n================================");
	}
	
	public boolean containsFile(String filename) {
		if (files.containsKey(filename)) {
			return true;
		}
		return false;
	}
	
	public boolean hasSpace(int amount) {
		if (amount > num_available_blocks) {
			return false;
		}
		return true;
	}
	
	public Character getFileChar(String filename) {
		return files.get(filename);
	}
	
	public int addFile(String filename, int blocks_required) {	
		char file_char = file_chars.get(0);
		file_chars.remove(0);
		int i = 0;
		int num_clusters = 0;
		boolean new_cluster = true;
		while (blocks_required > 0) {
			if (memory.get(i).equals('.')) {
				if (new_cluster) {
					num_clusters++;
					new_cluster = false;
				}
				memory.set(i, file_char);
				blocks_required--;
				num_available_blocks--;
			} else {
				new_cluster = true;
			}
			i++;
		}
		
		files.put(filename, file_char);
		
		return num_clusters;
	}
	
	public int removeFile(String filename) {
		char file = getFileChar(filename);
		int deallocated_blocks = 0;
		for (int i = 0; i < memory.size(); i++) {
			if (memory.get(i).equals(file)) {
				memory.set(i, '.');
				deallocated_blocks++;
				num_available_blocks++;
			}
		}
		file_chars.add(file);
		files.remove(filename);
		return deallocated_blocks;
	}
	
	public int readFile(int offset, int amount) {
		if (amount == 0) {
			return 0;
		}
		int num_blocks = 1;
		int starting_block = offset / blocksize + 1;
		amount -= (starting_block*blocksize - offset);
		while (amount > 0) {
			amount -= blocksize;
			num_blocks++;
		}
		return num_blocks;
	}
	
	public TreeSet<String> getFiles() {
		TreeSet<String> sorted_files = new TreeSet<String>();
		for (String key : files.keySet()) {
			sorted_files.add(key);
		}
		return sorted_files;
	}
}

