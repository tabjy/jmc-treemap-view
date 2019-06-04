package main.java.org.openjdk.jmc.flightrecorder.ext.treemap.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

public class Demo {
	private Shell shell;

	/**
	 * Launch the application.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Demo window = new Demo();
			window.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Open the window.
	 */
	public void open() {
		Display display = Display.getDefault();
		Instant then = Instant.now();
		createContents();
		Instant now = Instant.now();
		System.out.println(Duration.between(then, now));
		shell.open();
		shell.layout();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	protected void createContents() {
		shell = new Shell();
		// shell.setSize(640, 480);
		shell.setText("TreeMap Demo");
		shell.setLayout(new FillLayout(SWT.HORIZONTAL));

		TreeMapComposite treemap = new TreeMapComposite(shell, SWT.NONE);
		treemap.setBackground(new Color(Display.getDefault(), 100, 100, 100));

		TreeMapNode root = new TreeMapNode(0);
		root.setLabel("root");
		generateTree(root, 6, 6, false);

		treemap.setTree(root);
	}

	static int generatorCounter;
	static int id = 0;

	@SuppressWarnings("Duplicates")
	public static void generateTree(TreeMapNode root, int levels, int childrenNumber, boolean random) {
		TreeMapNode node;
		if (levels == 0) {
		} else {
			Random rand = new Random();
			int children = random ? rand.nextInt(childrenNumber) + 1 : childrenNumber;
			for (int i = 0; i < children; i++) {
				int val = rand.nextInt(50);
				id++;
				node = new TreeMapNode("Node #" + id, val);
				root.addChild(node);
				generatorCounter++;
			}
			for (TreeMapNode child : root.getChildren()) {
				generateTree(child, levels - 1, childrenNumber, random);
			}
		}
	}
}
