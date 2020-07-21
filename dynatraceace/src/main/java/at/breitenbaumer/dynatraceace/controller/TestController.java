package at.breitenbaumer.dynatraceace.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.KnownLinuxVirtualMachineImage;
import com.microsoft.azure.management.compute.KnownWindowsVirtualMachineImage;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;
import com.microsoft.azure.management.network.Network;
import com.microsoft.azure.management.resources.*;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.model.Creatable;
import com.microsoft.azure.management.resources.implementation.ResourceManager;
import com.microsoft.rest.LogLevel;

@RestController
public class TestController {
    final File credFile = new File(System.getenv("AZURE_AUTH_LOCATION"));
    static Azure azure;
    static ResourceManager rm;

    @PostConstruct
    public void init() {
        try {
            rm = ResourceManager.authenticate(ApplicationTokenCredentials.fromFile(credFile)).withSubscription("c0a97786-cce2-4cf3-9f1a-022e775c19ad");
            azure = Azure.configure()
                        .withLogLevel(LogLevel.BASIC)
                        .authenticate(credFile)
                        .withDefaultSubscription();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
    }

    @RequestMapping("/")
	public String index() {
        String result = "";
        PagedList<VirtualMachine> vms = azure.virtualMachines().list();
        System.out.println("Number of VMs in Subscription: " + vms.size());
        PagedList<GenericResource> res = azure.genericResources().list();
        for(GenericResource re : res){
            result += re.tags() + "<br>";
            result += re.name() + "<br>";
            result += re.type() + "<br>";
            result += "<br>";
            
        }
        for(VirtualMachine vm : vms){
            result += "Name: " + vm.name();
        }
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
                result += re.name();
                result += "</td><td>";
                result += re.type();
                result += "</td><td>";
                result += re.tags();
                result += "</td>";
                result += "</tr>";
        }
        result += "</table>";
        result += "<br>" + "<a href=\"http://localhost:8080/resourceGroup/" + resourceGroupName + "/tags\">Tags</a>";
		return result;
    }

    @RequestMapping("/resourceGroup/{resourceGroupName}/tags")
	public String getTagsForResourceGroup(@PathVariable("resourceGroupName") String resourceGroupName) {
        String result = "<h1>Resources in Group " + resourceGroupName + "</h1>";
        PagedList<GenericResource> res = azure.genericResources().listByResourceGroup(resourceGroupName);
        Map<String,String> tags = getAllTagsForResources(res);
        Map<String,String> newMap = tags.entrySet().stream().distinct().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Tag Key</b></td>" +
            "<td><b>Tag Value</b></td>" +
            "</tr>";
        for(Map.Entry<String,String> tag : newMap.entrySet()){
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
    
    @RequestMapping("/tags")
	public String getTags() {
        String result = "<h1>Tags</h1>";
        HashMap<String,String> tags = new HashMap<String,String>();
        PagedList<ResourceGroup> rgs = rm.resourceGroups().list();
        
        for(ResourceGroup rg : rgs){
                tags.putAll(getAllTags(rg));
        }
        Map<String,String> newMap = tags.entrySet().stream().distinct().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Tag Key</b></td>" +
            "<td><b>Tag Value</b></td>" +
            "</tr>";
        for(Map.Entry<String,String> tag : newMap.entrySet()){
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
                tags.putAll(re.tags());
        }
        return tags;
    }

    @RequestMapping("/tag/{tagKey}/{tagValue}")
	public String getResourcesForTagKey(@PathVariable("tagKey") String tagKey, @PathVariable("tagValue") String tagValue) {
        String result = "<h1>Resources with Tag" + tagKey + " = " + tagValue + "</h1>";
        
        return result;
    }

    @RequestMapping("/resourceGroup/{resourceGroupName}/{tagName}/{tagValue}")
    public String getResourcesForResourceGroupandTag(@PathVariable("resourceGroupName") String resourceGroupName,
                                              @PathVariable("tagName") String tagName,
                                              @PathVariable("tagValue") String tagValue) {
        String result = "<h1>Resources in Group " + resourceGroupName + "</h1>";
        PagedList<GenericResource> res = azure.genericResources().listByTag(resourceGroupName, tagName, tagValue);
        result += "<table border ='1'>" +
            "<tr>" +
            "<td><b>Name</b></td>" +
            "<td><b>Type</b></td>" +
            "<td><b>Tags</b></td>" +
            "</tr>";
        for(GenericResource re : res){
            result += "<tr>";
                result += "<td>";
                result += re.name();
                result += "</td><td>";
                result += re.type();
                result += "</td><td>";
                result += re.tags();
                result += "</td>";
                result += "</tr>";
        }
        result += "</table>";
		return result;
    }
}