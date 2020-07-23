package at.breitenbaumer.dynatraceace.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.cdn.ResourceType;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.graphrbac.ActiveDirectoryGroup;
import com.microsoft.azure.management.graphrbac.ActiveDirectoryUser;
import com.microsoft.azure.management.graphrbac.Permission;
import com.microsoft.azure.management.graphrbac.RoleAssignment;
import com.microsoft.azure.management.graphrbac.RoleAssignments;
import com.microsoft.azure.management.graphrbac.RoleDefinition;
import com.microsoft.azure.management.graphrbac.RoleDefinitions;
import com.microsoft.azure.management.graphrbac.ServicePrincipal;
import com.microsoft.azure.management.graphrbac.implementation.AuthorizationManagementClientImpl;
import com.microsoft.azure.management.locks.implementation.AuthorizationManager;
import com.microsoft.azure.management.resources.*;
import com.microsoft.azure.management.resources.implementation.ResourceManager;
import com.microsoft.rest.LogLevel;

@RestController
public class TestController {
    final File credFile = new File(System.getenv("AZURE_AUTH_LOCATION"));
    static String SUBSCRIPTION_STRING = "";
    static Azure azure;
    static ResourceManager rm;
    static AuthorizationManager am;
    

    @PostConstruct
    public void init() {
        try {
            azure = Azure.configure()
                        .withLogLevel(LogLevel.BASIC)
                        .authenticate(credFile)
                        .withDefaultSubscription();
            SUBSCRIPTION_STRING = azure.subscriptionId();
            rm = ResourceManager.configure().authenticate(ApplicationTokenCredentials.fromFile(credFile)).withSubscription(SUBSCRIPTION_STRING);
            am = AuthorizationManager.configure().authenticate(ApplicationTokenCredentials.fromFile(credFile), SUBSCRIPTION_STRING);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
    }

    @RequestMapping("/")
	public String index() {
        String result = "<h1>Azure SKD for JAVA Demo</h1>";
        result += "<h2>Browsing resource groups and loading resource metadata</h2>";
        result += "<h2>Current Subscription: " + azure.getCurrentSubscription().displayName() + "</h2>";
        result += "<a href=\"http://localhost:8080/resourceGroups\">Show all resource groups</a>" + "<br>";
		return result;
    }

    @RequestMapping("/users")
	public String getUsers() {
        String result = "<h1>Users</h1>";
        RoleAssignments ras = azure.accessManagement().roleAssignments();
        
        PagedList<ActiveDirectoryUser> users = ras.manager().users().list();
        for(ActiveDirectoryUser u : users){
            result += u.name() + "<br>";
            u.manager().roleAssignments().manager().servicePrincipals().list();
            PagedList<ActiveDirectoryGroup> groups = u.manager().groups().list();
            PagedList<RoleAssignment> raslist = u.manager().roleAssignments().listByScope("/subscriptions/" + SUBSCRIPTION_STRING + "/resourcegroups/rgname");
            for(ActiveDirectoryGroup g : groups){
                result += g.name() + "<br>";
                for(RoleAssignment ra : raslist){
                    result += ra.principalId() + "<br>";
                }
                
            }
        }
		return result;
    }

    @RequestMapping("/roleAssigmnets/{resourceGroupName}")
	public String getRoleAssigmnetsForResourceGroup(@PathVariable("resourceGroupName") String resourceGroupName) {
        String result = "<h1>Role Assigments</h1>";
        PagedList<RoleAssignment> ras = azure.accessManagement().roleAssignments().listByScope("/subscriptions/" + SUBSCRIPTION_STRING + "/resourcegroups/" + resourceGroupName);
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Principal</b></td>" +
            "<td><b>Role</b></td>" +
            "</tr>";
        for(RoleAssignment ra : ras){
            String pId = ra.principalId();
            
            result += "<tr>";
            result += "<td>";
            if(pId != null && pId != ""){
                ServicePrincipal sp = azure.accessManagement().servicePrincipals().getById(pId);
                if(sp != null){
                    result += azure.accessManagement().servicePrincipals().getById(pId).name() + "<br>";
                }
                else
                    result += "unknown" + "<br>";
            }
            else
            result += "unknown" + "<br>";
            result += "</td><td>";
            result += azure.accessManagement().roleDefinitions().getById(ra.roleDefinitionId()).roleName() + "<br>";
            result += "</td>";
            result += "</tr>";
        }
        result += "</table>";
        return result;
    }

    @RequestMapping("/roleDefinitions/{resourceGroupName}")
	public String getRoleDefinitionsForResourceGroup(@PathVariable("resourceGroupName") String resourceGroupName) {
        String result = "<h1>Role Definitions</h1>";
        PagedList<RoleDefinition> rdefs = azure.accessManagement().roleDefinitions().listByScope("/subscriptions/" + SUBSCRIPTION_STRING + "/resourcegroups/" + resourceGroupName);
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Name</b></td>" +
            "<td><b>Description</b></td>" +
            "<td><b>Role Type</b></td>" +
            "</tr>";
        for(RoleDefinition rd : rdefs){
            RoleDefinition roledef = azure.accessManagement().roleDefinitions().getById(rd.id());
            result += "<tr>";
            result += "<td>";
            result += roledef.roleName() + "<br>";
            result += "</td><td>";
            result += roledef.description() + "<br>";
            result += "</td><td>";
            result += roledef.inner().roleType() + "<br>";
            result += "</td>";
            result += "</tr>";
        }
        result += "</table>";
        return result;
    }

    @RequestMapping("/resourceGroups")
	public String getResourceGroups() {
        String result = "<h1>Resource Groups</h1>";
        PagedList<ResourceGroup> rgs = rm.resourceGroups().list();
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Name</b></td>" +
            "<td><b>Region</b></td>" +
            "<td><b>Tags</b></td>" +
            "</tr>";
        for(ResourceGroup rg : rgs){
            result += "<tr>";
            result += "<td>";
            result += "<a href=\"http://localhost:8080/resourceGroup/" + rg.name() + "\">" + rg.name() + "</a>";
            result += "</td><td>";
            result += rg.regionName();
            result += "</td><td>";
            result += rg.tags();
            result += "</td>";
            result += "</tr>";
        }
        result += "</table>";
		return result;
    }

    @RequestMapping("/resource/{resourceGroupName}")
	public String getDetailsForResource(@PathVariable("resourceGroupName") String resourceGroupName, @RequestParam("resourceId") String resourceId) {
        String result = "<h1>Resource Properties</h1>";
        GenericResource res = azure.genericResources().getById(resourceId);
        if(res.type() != null && res.type() != ""){
            if(res.type().equalsIgnoreCase("Microsoft.Compute/virtualMachines")){
                VirtualMachine vm = azure.virtualMachines().getById(res.id());
                result += "<table border ='1'>" +
                "<tr>" +
                "<td><b>Name</b></td>" +
                "<td><b>Size</b></td>" +
                "<td><b>OS Type</b></td>" +
                "<td><b>Admin User</b></td>" +
                "</tr>";
                result += "<tr>";
                result += "<td>";
                result += vm.name();
                result += "</td><td>";
                result += vm.size();
                result += "</td><td>";
                result += vm.osType().name();
                result += "</td><td>";
                result += vm.osProfile().adminUsername();
                result += "</td>";
                result += "</tr>";
            }
            else{
                result += "<table border ='1'>" +
                "<tr>" +
                "<td><b>Name</b></td>" +
                "<td><b>Properties</b></td>" +
                "</tr>";
                result += "<tr>";
                result += "<td>";
                result += res.name();
                result += "</td><td>";
                result += res.properties();
                result += "</td>";
                result += "</tr>";
            }
        }
        
        
        result += "</table>";
		return result;
    }

    @RequestMapping("/resourceGroup/{resourceGroupName}")
	public String getResourcesForResourceGroup(@PathVariable("resourceGroupName") String resourceGroupName) {
        String result = "<h1>Resources in Group " + resourceGroupName + "</h1>";
        PagedList<GenericResource> res = azure.genericResources().listByResourceGroup(resourceGroupName);
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Name</b></td>" +
            "<td><b>Type</b></td>" +
            "<td><b>Tags</b></td>" +
            "</tr>";
        for(GenericResource re : res){
            result += "<tr>";
                result += "<td>";
                result +=  "<a href=\"http://localhost:8080/resource/" + resourceGroupName + "?resourceId=" + re.id() + "\">" + re.name() + "</a>";
                result += "</td><td>";
                result += re.type();
                result += "</td><td>";
                result += re.tags();
                result += "</td>";
                result += "</tr>";
        }
        result += "</table>";
        result += "<br>" + "<a href=\"http://localhost:8080/resourceGroup/" + resourceGroupName + "/tags\">Tags</a>";
        result += "<br>" + "<a href=\"http://localhost:8080/roleAssigmnets/" + resourceGroupName + "\">Role Assigments</a>";
        result += "<br>" + "<a href=\"http://localhost:8080/roleDefinitions/" + resourceGroupName + "\">Role Definitions</a>";
		return result;
    }

    @RequestMapping("/resourceGroup/{resourceGroupName}/tags")
	public String getTagsForResourceGroup(@PathVariable("resourceGroupName") String resourceGroupName) {
        String result = "<h1>Tags per resource in ResourceGroup " + resourceGroupName + "</h1>";
        PagedList<GenericResource> res = azure.genericResources().listByResourceGroup(resourceGroupName);
        
        for(GenericResource re : res){
            Map<String,String> tags = re.tags();
            result += "<h2>" + re.name() + "</h2>";
            result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Tag Key</b></td>" +
            "<td><b>Tag Value</b></td>" +
            "</tr>";
            for(Map.Entry<String,String> tag : tags.entrySet()){
                result += "<tr>";
                result += "<td>";
                result += tag.getKey();
                result += "</td><td>";
                result += "<br>" + "<a href=\"http://localhost:8080/resourceGroup/" + resourceGroupName + "/tag?tagKey=" + tag.getKey() + "&tagValue=" + tag.getValue() + "\">" + tag.getValue() + "</a>";
                result += "</td>";
                result += "</tr>";
            }
            result += "</table>";
        }
        
		return result;
    }
    
    @RequestMapping("/tags")
	public String getTags() {
        String result = "<h1>Tags</h1>";
        HashMap<String,String> tags = new HashMap<String,String>();
        PagedList<ResourceGroup> rgs = rm.resourceGroups().list();
        
        for(ResourceGroup rg : rgs){
                tags.putAll(getAllTags(rg));
        }
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Tag Key</b></td>" +
            "<td><b>Tag Value</b></td>" +
            "</tr>";
        for(Map.Entry<String,String> tag : tags.entrySet()){
            result += "<tr>";
            result += "<td>";
            result += tag.getKey();
            result += "</td><td>";
            result += tag.getValue();
            result += "</td>";
            result += "</tr>";
        }
        result += "</table>";
		return result;
    }
    
    private Map<String,String> getAllTags(ResourceGroup rg){
        HashMap<String,String> tags = new HashMap<String,String>();
        tags.putAll(rg.tags());
        PagedList<GenericResource> res = azure.genericResources().listByResourceGroup(rg.name());
        for(GenericResource re : res){
                System.out.println("Resource: " + re.name());
                System.out.println("Number of Tags: " + re.tags().size());
                tags.putAll(re.tags());
        }
        System.out.println("Number of total Tags: " + tags.size());
        return tags;
    }

    private Map<String,String> getAllTagsForResources(PagedList<GenericResource> res){
        HashMap<String,String> tags = new HashMap<String,String>();

        for(GenericResource re : res){
                re.tags().entrySet().toArray();
                tags.putAll(re.tags());
        }
        return tags;
    }


    @RequestMapping("/resourceGroup/{resourceGroupName}/tag")
    public String getResourcesForResourceGroupandTag(@PathVariable("resourceGroupName") String resourceGroupName,
                                                @RequestParam("tagKey") String tagKey,
                                                @RequestParam("tagValue") String tagValue) {
        String result = "<h1>Resources for tag " + tagKey + " = " + tagValue + "</h1>";
        PagedList<GenericResource> res = azure.genericResources().listByTag(resourceGroupName, tagKey, tagValue);
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Name</b></td>" +
            "<td><b>Type</b></td>" +
            "</tr>";
        for(GenericResource re : res){
            result += "<tr>";
                result += "<td>";
                result += re.name();
                result += "</td><td>";
                result += re.type();
                result += "</td>";
                result += "</tr>";
        }
        result += "</table>";
		return result;
    }
}