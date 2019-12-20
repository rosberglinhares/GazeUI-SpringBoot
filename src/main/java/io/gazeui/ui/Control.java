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

public abstract class Control implements Cloneable {
    
    private ContainerControl parent;
    private Window window;
    // An ID is necessary to link the client control that raised some event to your underlying server object.
    // This ID is autogenerated when the control is added to some window. Another strategy could be generate the ID
    // in the getRenderScript method, but this would be another step that could overload the render process.
    private String clientId;
    
    public ContainerControl getParent() {
        return this.parent;
    }
    
    public Window getWindow() {
        if (this.window == null) {
            Control control = this;
            
            while (control != null && !(control instanceof Window)) {
                control = control.getParent();
            }
            
            this.window = (Window)control;
        }
        
        return this.window;
    }
    
    public String getClientId() {
        return this.clientId;
    }
    
    void onAddToCollection(ContainerControl parent) {
        boolean isControlWithoutWindow = this.getWindow() == null;
        
        // Remove the new control from its old parent (if any)
        if (this.getParent() != null) {
            // The remove method will call onBeforeRemoveFromCollection
            this.getParent().getControls().remove(this);
        }
        
        this.parent = parent;
        
        if (this.getWindow() != null && isControlWithoutWindow) {
            // When a control gain a Window, we must set the ID of the control and all of its descendants
            this.setControlTreeIds(this);
        }
    }
    
    private void setControlTreeIds(Control control) {
        // For controls that were removed from the window and added again:
        //
        // 1. While the control was without a window, another controls could be added to its tree, so they will not
        //    have a client ID. Because of this, we need to traverse all the tree to reach these controls.
        //
        // 2. The control ID will be preserved.
        if (control.getClientId() == null) {
            control.clientId = this.getWindow().generateAutomaticControlId();
        }
        
        if (control instanceof ContainerControl) {
            for (Control childControl : ((ContainerControl)control).getControls()) {
                this.setControlTreeIds(childControl);
            }
        }
    }
    
    void onRemoveFromCollection() {
        this.parent = null;
        this.detachControlTree(this);
    }
    
    private void detachControlTree(Control control) {
        // Remove the cached value
        control.window = null;
        
        if (control instanceof ContainerControl) {
            for (Control childControl : ((ContainerControl)control).getControls()) {
                this.detachControlTree(childControl);
            }
        }
    }
    
    @Override
    protected Control clone() {
        // This method is only to make the clone method visible for classes in the same package.
        // This will allow the ContainerControl to clone your child controls.
        
        try {
            return (Control)super.clone();
        } catch (CloneNotSupportedException ex) {
            // Never happens, once Control is implementing Cloneable. 
            throw new RuntimeException(ex);
        }
    }
    
    @Override
    public String toString() {
        if (this.getClientId() != null) {
            return String.format("%s, Id: '%s'", this.getClass().getSimpleName(), this.getClientId());
        } else {
            return this.getClass().getSimpleName() + "@" + Integer.toHexString(this.hashCode());
        }
    }
    
    /**
     * A script that can be run to find this control on the client side. After running this script, it is possible
     * to use the token returned by the {@link #identificationToken()} method to reach the control.
     */
    protected String selectionScript() {
        return String.format("var %1$s = document.getElementById('%1$s');\n", this.getClientId());
    }
    
    /**
     * A token that can be used to reach this control on the client side. This token can be used after running the
     * control's rendering script or after running the script returned by the {@link #selectionScript()} method.
     */
    protected String identificationToken() {
        return this.getClientId();
    }
    
    protected abstract String getRenderScript(Control previousControlState);
}