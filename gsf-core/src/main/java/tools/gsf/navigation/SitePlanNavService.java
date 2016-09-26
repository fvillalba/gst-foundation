/*
 * Copyright 2016 Function1. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.gsf.navigation;

import COM.FutureTense.Interfaces.ICS;
import com.fatwire.assetapi.data.AssetId;
import com.fatwire.cs.core.db.PreparedStmt;
import tools.gsf.facade.assetapi.AssetIdUtils;
import tools.gsf.facade.assetapi.asset.TemplateAsset;
import tools.gsf.facade.runtag.render.LogDep;
import tools.gsf.facade.sql.Row;
import tools.gsf.facade.sql.SqlHelper;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple navigation service implementation that loads objects from the Site Plan. Supports populating node data via
 * a dedicated method that can be overridden to load any data that is required, as long as it can be presented
 * as a TemplateAsset.
 *
 * Reads the full site plan tree in one query, but only reads asset data for nodes that it is going to actually return.
 *
 * @author Tony Field
 * @since 2016-07-06
 */
public abstract class SitePlanNavService implements NavService<AssetNode, AssetId, AssetId> {
	
	private static final Logger LOG = LoggerFactory.getLogger(SitePlanNavService.class);

    private final ICS ics;
    private final Map<AssetId,SimpleAssetNode[]> nodesById = new HashMap<>();

    private static final PreparedStmt NAVIGATION_TREE_DUMP = new PreparedStmt(
            "select * from SITEPLANTREE where ncode = 'Placed'",
            Collections.singletonList("SITEPLANTREE"));

    public SitePlanNavService(ICS ics) {

        this.ics = ics;

        // read the site plan tree in one massive query
        Map<Long, SitePlanTreeData> rowMap = new HashMap<>();

        for (Row row : SqlHelper.select(ics, NAVIGATION_TREE_DUMP, NAVIGATION_TREE_DUMP.newParam())) {
            SitePlanTreeData nodeInfo = new SitePlanTreeData(row);
            rowMap.put(nodeInfo.nid, nodeInfo);
        }

        // create Node objects
        Map<Long, SimpleAssetNode> nidNodeMap = new HashMap<>();
        for (long nid : rowMap.keySet()) {
            SimpleAssetNode node = new SimpleAssetNode(rowMap.get(nid).assetId);
            nidNodeMap.put(nid, node);
        }

        // hook up parent-child relationships
        for (long nid : rowMap.keySet()) {
            SitePlanTreeData sptRow = rowMap.get(nid);
            SimpleAssetNode node = nidNodeMap.get(nid);
            SimpleAssetNode parent = nidNodeMap.get(sptRow.nparentid);
            if (parent != null) {
                node.setParent(parent);
                parent.addChild(sptRow.nrank, node); // this ranks them too!
            }

            // Stash for later. Probably won't have many duplicates so optimize and don't create too many lists
            AssetId assetId = node.getId();
            SimpleAssetNode[] a1 = nodesById.get(assetId);
            if (a1 == null) {
                a1 = new SimpleAssetNode[1];
                a1[0] = node;
                nodesById.put(assetId, a1);
            } else {
                SimpleAssetNode[] a2 = new SimpleAssetNode[a1.length+1];
                System.arraycopy(a1, 0, a2, 0, a1.length);
                a2[a1.length] = node;
                nodesById.put(assetId, a2);
            }
        }
    }

    private static class SitePlanTreeData {
        final long nid;
        final long nparentid;
        final int nrank;
        final AssetId assetId;

        SitePlanTreeData(Row row) {
            nid = row.getLong("nid");
            nparentid = row.getLong("nparentid");
            nrank = row.getInt("nrank");
            assetId = AssetIdUtils.createAssetId(row.getString("otype"), row.getLong("oid"));
        }

        @Override
        public String toString() {
            return "SitePlanTreeData{" +
                    "nid=" + nid +
                    ", nparentid=" + nparentid +
                    ", nrank=" + nrank +
                    ", assetId=" + assetId +
                    '}';
        }
    }

    public List<AssetNode> loadNav(AssetId sitePlan) {

        if (sitePlan == null) {
            throw new IllegalArgumentException("Null param not allowed");
        }

        // find the requested structure
        AssetNode[] spNodes = nodesById.get(sitePlan);
        if (spNodes == null) throw new IllegalArgumentException("Could not locate nav structure corresponding to "+sitePlan);
        if (spNodes.length > 1) throw new IllegalStateException("Cannot have more than one site plan node with the same id in the tree");
        AssetNode requestedRoot = spNodes[0]; // never null

        // populate asset data into the requested structure
        _populateNode(requestedRoot);

        // return the loaded children of the structure root
        return requestedRoot.getChildren();
    }
    
    private void _populateNodesRec(Collection<AssetNode> nodesToPopulate, AssetNode emptyNode) {
    	// put the current emptyNode in the list of nodes to populate
        nodesToPopulate.add(emptyNode);
        for (AssetNode child : emptyNode.getChildren()) {
        	// put all descendants of the current emptyNode in
        	// the list of nodes to populate
        	_populateNodesRec(nodesToPopulate, child);
        }
    }

    private void _populateNode(AssetNode entryNode) {
        Collection<AssetNode> nodesToPopulate = new HashSet<AssetNode>();
        _populateNodesRec(nodesToPopulate, entryNode);

        // fill 'em up
        for (AssetNode node : nodesToPopulate) {
            AssetId id = node.getId();
            LogDep.logDep(ics, id);
            TemplateAsset data = getNodeData(id);
            if (data == null) {
                throw new IllegalStateException("Null node data returned for id " + id);
            }
            SimpleAssetNode san = _asSimpleAssetNode(node);
            san.setAsset(data);
        }
    }

    /**
     * We can't modify AssetNode objects, but we can modify SimpleAssetNodes. We do have a map of SimpleAssetNode
     * objects that we can look through though, so look through all of them and find the handle to the SimpleAssetNodes
     * corresponding to the input.
     * @param node asset node
     * @return asset node as simple asset node
     */
    private SimpleAssetNode _asSimpleAssetNode(AssetNode node) {
        for (SimpleAssetNode san : nodesById.get(node.getId())) {
            if (san.equals(node))
                return san;
        }
        throw new IllegalStateException("Could not find SimpleAsseNode corresponding to AssetNode: "+node);
    }

    /**
     * Method to retrieve data that will be loaded into a node. Implementing classes should take care
     * to be very efficient both for cpu time as well as memory usage.
     * @param id asset ID to load
     * @return asset data in the form of a TemplateAsset, never null
     */
    protected abstract TemplateAsset getNodeData(AssetId id);

    public List<AssetNode> getBreadcrumb(AssetId id) {

        if (id == null) {
            throw new IllegalArgumentException("Cannot calculate breadcrumb of a null asset");
        }

        Collection<List<AssetNode>> breadcrumbs = new ArrayList<>();
        for (AssetNode node : nodesById.get(id)) {
            breadcrumbs.add(getBreadcrumbForNode(node));
        }

        List<AssetNode> breadcrumb = chooseBreadcrumb(breadcrumbs);

        // populate only those nodes in the breadcrumb
        // and their corresponding children
        for (AssetNode breadcrumbNode : breadcrumb) {
        	_populateNode(breadcrumbNode);
        }

        return breadcrumb;
    }

    /**
     * Get the breadcrumb corresponding to the specified node.
     *
     * @implSpec
     * Default implementation simply uses the specified node's parents.
     *
     * @param node the node whose breadcrumb needs to be calculated
     * @return the breadcrumb
     */
    protected List<AssetNode> getBreadcrumbForNode(AssetNode node) {
        List<AssetNode> ancestors = new ArrayList<>();
        do {
        	// Make sure we exclude any parents which
        	// are not Page assets
        	if (node.getId().getType().equals("Page")) {
        		ancestors.add(node);
        	}
            node = node.getParent();
        } while (node != null);
        Collections.reverse(ancestors);
        return ancestors;
    }

    /**
     * Pick which breadcrumb to return if more than one path has been found.
     *
     * @implSpec
     * Default implementation simply returns the first one returned by the specified collection's iterator.
     *
     * @param options candidate breadcrumbs
     * @return the breadcrumb to use.
     */
    protected List<AssetNode> chooseBreadcrumb(Collection<List<AssetNode>> options) {
        return options.iterator().next();
    }
}