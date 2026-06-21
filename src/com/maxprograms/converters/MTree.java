/*******************************************************************************
 * Copyright (c) 2018 - 2026 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

public class MTree<T extends Serializable> implements Serializable {

	public static class Node<T extends Serializable> implements Serializable {
		private static final long serialVersionUID = 263173588184503815L;

		private T data;
		private Node<T> parent;
		private ArrayList<Node<T>> children;

		public Node(T value) {
			data = value;
			children = new ArrayList<>();
		}

		public void addChild(Node<T> node) {
			node.parent = this;
			children.add(node);
		}

		public void removeChild(Node<T> node) {
			children.remove(node);
		}

		public Node<T> getChild(T value) {
			for (Node<T> child : children) {
				if (value.equals(child.data)) {
					return child;
				}
			}
			return null;
		}

		public Iterator<Node<T>> iterator() {
			return children.iterator();
		}

		public Node<T> getParent() {
			return parent;
		}

		public T getData() {
			return data;
		}

		public int size() {
			return children.size();
		}

		public Node<T> getChild(int i) {
			return children.get(i);
		}
	}

	private static final long serialVersionUID = 5458283808659252781L;
	private Node<T> root;

	public MTree(T rootData) {
		root = new Node<>(rootData);
	}

	public MTree(Node<T> node) {
		root = node;
	}

	public Node<T> getRoot() {
		return root;
	}

	public MTree<T> prune() {
		Node<T> newRoot = root;
		while (newRoot.size() == 1) {
			newRoot = newRoot.getChild(0);
		}
		return new MTree<>(newRoot);
	}
}