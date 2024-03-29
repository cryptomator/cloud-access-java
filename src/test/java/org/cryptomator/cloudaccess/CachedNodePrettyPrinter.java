package org.cryptomator.cloudaccess;

import java.util.Comparator;

class CachedNodePrettyPrinter {

	public static String prettyPrint(CachedNode node) {
		var sb = new StringBuilder();
		prettyPrint(node, sb, 0);
		return sb.toString();
	}

	private static void prettyPrint(CachedNode node, StringBuilder sb, int depth) {
		for (int i = 0; i < depth; i++) {
			sb.append("  ");
		}
		sb.append(node.getName());
		var data = node.getData();
		var children = node.getChildren();
		if (data != null) {
			sb.append(" - ").append(data);
		}
		if (!children.isEmpty()) {
			sb.append("/\n");
			children.stream().sorted(Comparator.comparing(CachedNode::getName)).forEachOrdered(c -> prettyPrint(c, sb, depth + 1));
		} else {
			sb.append("\n");
		}
	}

}
