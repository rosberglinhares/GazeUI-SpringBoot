/*
 * MIT License
 * 
 * Copyright (c) 2019 Rosberg Linhares (rosberglinhares@gmail.com)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.gazeui.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;

class ControlCollection implements List<Control> {
    /*
     * 1. The collection must be in the same package of Control to be possible to call the
     *    onAddToCollection and onRemoveFromCollection methods. We think to be unnecessary to use observers
     *    for this operation.
     * 
     * 2. The ideal collection here is one that does not allow duplicates, maintains insertion order and allows access
     *    by index.
     *     
     *    2.1. A LinkedHashSet does not allow duplicates and maintains insertion order, but Set implementations
     *         do not offer access by index.
     *    2.2. List implementations maintain insertion order and allow access by index, but do not restrict duplicate
     *         elements.
     *    
     *    Therefore, we decided to use List and restrict duplicate elements by ourselves.
     */
    
    private final ContainerControl owner;
    // Doing by composition makes possible to change the inner list type, if necessary.
    private final List<Control> innerList;
    
    public ControlCollection(ContainerControl owner) {
        // The Longest Common Subsequence algorithm requires a lot of access by index, so the use of an ArrayList.
        this(owner, new ArrayList<Control>());
    }
    
    private ControlCollection(ContainerControl owner, List<Control> innerList) {
        this.owner = Objects.requireNonNull(owner, ErrorMessage.CONTROL_COLLECTION_MUST_HAVE_OWNER.getMessage());
        this.innerList = innerList;
    }
    
    @Override
    public boolean add(Control control) {
        if (control.getParent() == this.owner) {
            // Send the control to the end of the list
            this.innerList.remove(control);
        } else {
            control.onAddToCollection(this.owner);
        }
        
        return this.innerList.add(control);
    }
    
    @Override
    public void add(int index, Control control) {
        if (control.getParent() == this.owner) {
            this.innerList.remove(control);
        } else {
            control.onAddToCollection(this.owner);
        }
        
        this.innerList.add(index, control);
    }
    
    @Override
    public boolean addAll(Collection<? extends Control> c) {
        Set<Control> uniqueCollection = new LinkedHashSet<>(c);
        
        for (Control control : uniqueCollection) {
            if (control.getParent() == this.owner) {
                this.innerList.remove(control);
            } else {
                control.onAddToCollection(this.owner);
            }
        }
        
        return this.innerList.addAll(uniqueCollection);
    }
    
    @Override
    public boolean addAll(int index, Collection<? extends Control> c) {
        Set<Control> uniqueCollection = new LinkedHashSet<>(c);
        
        for (Control control : uniqueCollection) {
            if (control.getParent() == this.owner) {
                this.innerList.remove(control);
            } else {
                control.onAddToCollection(this.owner);
            }
        }
        
        return this.innerList.addAll(index, uniqueCollection);
    }
    
    @Override
    public Control set(int index, Control control) {
        Control previousControl;
        
        if (control.getParent() == this.owner) {
            previousControl = this.innerList.get(index);
            
            // Once we are forcing unique items on the list, it is necessary only to remove the first occurrence.
            this.innerList.remove(control);
            this.innerList.add(index, control);
        } else {
            previousControl = this.innerList.set(index, control);
            
            previousControl.onRemoveFromCollection();
            control.onAddToCollection(this.owner);
        }
        
        return previousControl;
    }
    
    @Override
    public boolean remove(Object o) {
        boolean result = this.innerList.remove(o);
        
        if (result) {
            ((Control)o).onRemoveFromCollection();
        }
        
        return result;
    }
    
    @Override
    public Control remove(int index) {
        Control removedControl = this.innerList.remove(index);
        removedControl.onRemoveFromCollection();
        
        return removedControl;
    }
    
    @Override
    public boolean removeAll(Collection<?> c) {
        // Avoid call the onRemoveFromCollection method multiple times for the same element
        Set<Object> uniqueCollection = new LinkedHashSet<>(c);
        
        for (Object element : uniqueCollection) {
            if (this.innerList.contains(element)) {
                ((Control)element).onRemoveFromCollection();
            }
        }
        
        return this.innerList.removeAll(c);
    }
    
    @Override
    public boolean retainAll(Collection<?> c) {
        for (Control control : this.innerList) {
            if (!c.contains(control)) {
                control.onRemoveFromCollection();
            }
        }
        
        return this.innerList.retainAll(c);
    }
    
    @Override
    public void clear() {
        for (Control control : this.innerList) {
            control.onRemoveFromCollection();
        }
        
        this.innerList.clear();
    }
    
    @Override
    public ControlCollection subList(int fromIndex, int toIndex) {
        List<Control> subList = this.innerList.subList(fromIndex, toIndex);
        
        return new ControlCollection(this.owner, subList);
    }
    
    @Override
    public Iterator<Control> iterator() {
        return new ControlCollectionIterator(this.innerList.iterator());
    }
    
    @Override
    public ListIterator<Control> listIterator() {
        return new ControlCollectionListIterator(this.innerList.listIterator());
    }
    
    @Override
    public ListIterator<Control> listIterator(int index) {
        return new ControlCollectionListIterator(this.innerList.listIterator(index));
    }
    
    private class ControlCollectionIterator implements Iterator<Control> {
        private final Iterator<Control> innerIterator;
        private Control lastReturnedElement;
        
        public ControlCollectionIterator(Iterator<Control> innerIterator) {
            this.innerIterator = innerIterator;
        }
        
        protected Control getLastReturnedElement() {
            return this.lastReturnedElement;
        }
        
        protected void setLastReturnedElement(Control lastReturnedElement) {
            this.lastReturnedElement = lastReturnedElement;
        }
        
        @Override
        public Control next() {
            Control next = this.innerIterator.next();
            this.setLastReturnedElement(next);
            
            return next;
        }
        
        @Override
        public void remove() {
            this.innerIterator.remove();
            this.getLastReturnedElement().onRemoveFromCollection();
        }
        
        @Override
        public boolean hasNext() {
            return this.innerIterator.hasNext();
        }
    }
    
    private class ControlCollectionListIterator extends ControlCollectionIterator implements ListIterator<Control> {
        private final ListIterator<Control> innerIterator;
        
        public ControlCollectionListIterator(ListIterator<Control> innerIterator) {
            super(innerIterator);
            this.innerIterator = innerIterator;
        }
        
        @Override
        public Control previous() {
            Control previous = this.innerIterator.previous();
            this.setLastReturnedElement(previous);
            
            return previous;
        }
        
        @Override
        public void add(Control control) {
            if (control.getParent() != ControlCollection.this.owner) {
                this.innerIterator.add(control);
                control.onAddToCollection(ControlCollection.this.owner);
            } else {
                // Because we are avoiding duplicates on the list, here we would have to remove the element first
                // and add it at the correct position, but this would throw a ConcurrentModificationException because
                // it is not possible to remove another element while iterating over the list. So we decided to make
                // this operation not allowed.
                throw new UnsupportedOperationException(
                        ErrorMessage.CONTROL_COLLECTION_ADD_SET_EXISTING_ELEMENT_USING_ITERATOR.getMessage());
            }
        }
        
        @Override
        public void set(Control control) {
            if (control.getParent() != ControlCollection.this.owner) {
                this.innerIterator.set(control);
                
                this.getLastReturnedElement().onRemoveFromCollection();
                control.onAddToCollection(ControlCollection.this.owner);
            } else {
                throw new UnsupportedOperationException(
                        ErrorMessage.CONTROL_COLLECTION_ADD_SET_EXISTING_ELEMENT_USING_ITERATOR.getMessage());
            }
        }
        
        @Override
        public boolean hasPrevious() {
            return this.innerIterator.hasPrevious();
        }
        
        @Override
        public int previousIndex() {
            return this.innerIterator.previousIndex();
        }
        
        @Override
        public int nextIndex() {
            return this.innerIterator.nextIndex();
        }
    }
    
    // Non-modification operations
    
    @Override
    public boolean contains(Object o) {
        return this.innerList.contains(o);
    }
    
    @Override
    public boolean containsAll(Collection<?> c) {
        return this.innerList.containsAll(c);
    }
    
    @Override
    public Control get(int index) {
        return this.innerList.get(index);
    }
    
    @Override
    public int indexOf(Object o) {
        return this.innerList.indexOf(o);
    }
    
    @Override
    public boolean isEmpty() {
        return this.innerList.isEmpty();
    }
    
    @Override
    public int lastIndexOf(Object o) {
        return this.innerList.lastIndexOf(o);
    }
    
    @Override
    public int size() {
        return this.innerList.size();
    }
    
    @Override
    public Object[] toArray() {
        return this.innerList.toArray();
    }
    
    @Override
    public <T> T[] toArray(T[] a) {
        return this.innerList.toArray(a);
    }
}