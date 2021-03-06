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
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;

import io.gazeui.ui.collections.Lists;

public class ContainerControl extends Control {

    private static final Comparator<Control> clientIdComparator;
    
    static {
        clientIdComparator = new Comparator<Control>() {
            @Override
            public int compare(Control c1, Control c2) {
                return c1.getClientId().compareTo(c2.getClientId());
            }
        };
    }
    
    private List<Control> controls;
    
    public List<Control> getControls() {
        if (this.controls == null) {
            // To generate the automatic ID for controls, we have to know when they are added.
            // So the use of a custom control collection.
            this.controls = new ControlCollection(this);
        }
        
        return this.controls;
    }
    
    @Override
    protected ContainerControl clone() {
        ContainerControl clonedContainerControl = (ContainerControl)super.clone();
        
        // The cloned collection will not suffer any operation, so it is not necessary to be a ControlCollection
        clonedContainerControl.controls = new ArrayList<Control>(this.getControls().size());
        
        // Doing a deep copy of child controls
        for (Control control : this.getControls()) {
            clonedContainerControl.getControls().add(control.clone());
        }
        
        return clonedContainerControl;
    }
    
    /**
     * A script that will be responsible to create the container for child controls on the client side.
     */
    protected String creationScript() {
        return String.format(
                "var %1$s = document.createElement('div');\n" +
                "%1$s.id = '%1$s';\n", this.getClientId());
    }
    
    @Override
    protected String getRenderScript(Control previousControlState) {
        if (previousControlState == null) {
            return this.getCreateRenderScript();
        } else {
            return this.getUpdateRenderScript((ContainerControl)previousControlState);
        }
    }
    
    private String getCreateRenderScript() {
        StringBuilder sbScript = new StringBuilder();
        
        sbScript.append(this.creationScript());
        
        for (Control childControl : this.getControls()) {
            String createChildControlScript = childControl.getRenderScript(null);
            
            sbScript.append(createChildControlScript);
            sbScript.append(String.format("%s.appendChild(%s);\n",
                    this.identificationToken(), childControl.identificationToken()));
        }
        
        return sbScript.toString();
    }
    
    private String getUpdateRenderScript(ContainerControl previousControlState) {
        // We expect that operations of adding, removing and changing child controls order will not be so common.
        // So we check first for the case which at most updates on child controls were made. Doing that we avoid
        // running the Longest Common Subsequence algorithm (a heavy operation) for this simple case.
        if (this.listsWithSameStructure(this.getControls(), previousControlState.getControls())) {
            StringBuilder sbUpdateChildControlsScript = new StringBuilder();
            
            Iterator<Control> currentChildControlsIterator = this.getControls().iterator();
            Iterator<Control> previousChildControlsIterator = previousControlState.getControls().iterator();
            
            while (currentChildControlsIterator.hasNext()) {
                Control childControl = currentChildControlsIterator.next();
                Control previousChildControl = previousChildControlsIterator.next();
                
                sbUpdateChildControlsScript.append(childControl.getRenderScript(previousChildControl));
            }
            
            return sbUpdateChildControlsScript.toString();
        } else {
            StringBuilder sbRemoveChildControlsScript = new StringBuilder();
            StringBuilder sbUpdateChildControlsScript = new StringBuilder();
            StringBuilder sbAddAndChangeOrderChildControlsScript = new StringBuilder();
            
            List<Control> lcs = Lists.longestCommonSubsequence(this.getControls(),
                    previousControlState.getControls(), clientIdComparator);
            
            // These maps are used only to have constant-time performance for get operations.
            // Doing that we avoid quadratic time complexity O(n^2).
            Map<String, Control> lcsMap = Lists.toMap(lcs, Control::getClientId, Function.identity());
            Map<String, Control> currentChildControlsMap = Lists.toMap(this.getControls(), Control::getClientId, Function.identity());
            Map<String, Control> previousChildControlsMap = Lists.toMap(previousControlState.getControls(), Control::getClientId, Function.identity());
            
            // 1. Remove
            
            for (Control previousChildControl : previousControlState.getControls()) {
                if (!currentChildControlsMap.containsKey(previousChildControl.getClientId())) {
                    sbRemoveChildControlsScript.append(previousChildControl.selectionScript());
                    sbRemoveChildControlsScript.append(String.format("%s.remove();\n", previousChildControl.identificationToken()));
                }
            }
            
            // 2. Update, Add and Order Changed
            
            ListIterator<Control> reverseListIterator = this.getControls().listIterator(this.getControls().size());
            Control previousLoopChildControl = null;
            // If a variable pointing to the previous control in the loop was already created
            boolean previousLoopChildControlIdentified = false;
            
            // Here we are iterating in reverse order to make possible use the Node.insertBefore() DOM method.
            // At 12/2019, the ChildNode.after() method is marked experimental in the MDN website and is not
            // supported by Safari:
            //   [1]: https://developer.mozilla.org/en-US/docs/Web/API/ChildNode/after
            //   [2]: https://caniuse.com/#feat=mdn-api_childnode_after
            while (reverseListIterator.hasPrevious()) {
                Control childControl = reverseListIterator.previous();
                // If a variable pointing to the control was already created
                boolean childControlIdentified = false;
                
                // There is five different situations to a control here:
                // 
                //   1. Belongs to the lcs
                //     1.1. Was not updated
                //     1.2. Was updated
                //   
                //   2. Does not belong to the lcs
                //     2.1. Was moved
                //       2.1.1. Was not updated
                //       2.1.2. Was updated
                //     2.2. Was added
                //
                
                if (previousChildControlsMap.containsKey(childControl.getClientId())) {
                    Control previousChildControlState = previousChildControlsMap.get(childControl.getClientId());
                    String updateChildControlScript = childControl.getRenderScript(previousChildControlState);
                    
                    if (!updateChildControlScript.isEmpty()) {
                        sbUpdateChildControlsScript.append(updateChildControlScript);
                        childControlIdentified = true;
                    }
                }
                
                if (!lcsMap.containsKey(childControl.getClientId())) {
                    if (previousChildControlsMap.containsKey(childControl.getClientId())) {
                        // The element changed its order
                        if (!childControlIdentified) {
                            sbAddAndChangeOrderChildControlsScript.append(childControl.selectionScript());
                            childControlIdentified = true;
                        }
                    } else {
                        // The element was added
                        sbAddAndChangeOrderChildControlsScript.append(childControl.getRenderScript(null));
                        childControlIdentified = true;
                    }
                    
                    String previousLoopChildControlIdentificationToken;
                    
                    if (previousLoopChildControl != null) {
                        if (!previousLoopChildControlIdentified) {
                            sbAddAndChangeOrderChildControlsScript.append(previousLoopChildControl.selectionScript());
                        }
                        
                        previousLoopChildControlIdentificationToken = previousLoopChildControl.identificationToken();
                    } else {
                        // If referenceNode is null, the newNode is inserted at the end of the list of child nodes.
                        previousLoopChildControlIdentificationToken = null;
                    }
                    
                    sbAddAndChangeOrderChildControlsScript.append(String.format("%s.insertBefore(%s, %s);\n",
                            this.identificationToken(), childControl.identificationToken(), previousLoopChildControlIdentificationToken));
                }
                
                previousLoopChildControl = childControl;
                previousLoopChildControlIdentified = childControlIdentified;
            }
            
            StringBuilder sbScript;
            
            if (sbAddAndChangeOrderChildControlsScript.length() > 0) {
                // To use the insertBefore DOM method, we have to have a reference to the parent node,
                // which is this control itself.
                String selectionScript = this.selectionScript();
                
                sbScript = new StringBuilder(sbRemoveChildControlsScript.length() +
                        sbUpdateChildControlsScript.length() +
                        selectionScript.length() + sbAddAndChangeOrderChildControlsScript.length());
                
                sbScript.append(sbRemoveChildControlsScript);
                sbScript.append(sbUpdateChildControlsScript);
                sbScript.append(selectionScript);
                sbScript.append(sbAddAndChangeOrderChildControlsScript);
            } else {
                sbScript = new StringBuilder(sbRemoveChildControlsScript.length() +
                        sbUpdateChildControlsScript.length());
                
                sbScript.append(sbRemoveChildControlsScript);
                sbScript.append(sbUpdateChildControlsScript);
            }
            
            return sbScript.toString();
        }
    }
    
    private boolean listsWithSameStructure(List<Control> list1, List<Control> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }
        
        Iterator<Control> it1 = list1.iterator();
        Iterator<Control> it2 = list2.iterator();
        
        while (it1.hasNext()) {
            if (clientIdComparator.compare(it1.next(), it2.next()) != 0) {
                return false;
            }
        }
        
        return true;
    }
}