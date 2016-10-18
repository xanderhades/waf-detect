/*
 * Burp WAFDetect extension
 */
package burp;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.io.PrintWriter;


public class BurpExtender implements IBurpExtender, IScannerCheck
{
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    private static final String EXT_NAME = "WAFDetect";
    //private static final byte[] GREP_STRING = "ASP.NET".getBytes();
    private static HashMap wf = new HashMap(); 
    //private static final byte[] INJ_TEST = "|".getBytes();
    //private static final byte[] INJ_ERROR = "Unexpected pipe".getBytes();
    private PrintWriter stdout;
    private PrintWriter stderr;
    
    //
    // implement IBurpExtender
    //
    
    @Override
    public void registerExtenderCallbacks(final IBurpExtenderCallbacks callbacks)
    {
        // Create list 
        wf.put("Incapsula WAF"    , "incap_ses");
        wf.put("Incapsula CDN"    , "X-CDN: Incapsula");
                
        // keep a reference to our callbacks object
        this.callbacks = callbacks;
        
        // obtain an extension helpers object
        helpers = callbacks.getHelpers();
        
        // set our extension name
        callbacks.setExtensionName(EXT_NAME);

        // obtain our output and error streams
        stdout = new PrintWriter(callbacks.getStdout(), true);
        stderr = new PrintWriter(callbacks.getStderr(), true);
        
        // write messages to output streams
        stdout.println("Started " + EXT_NAME);
        callbacks.issueAlert("Started " + EXT_NAME);
        //throw new RuntimeException("Hello exceptions");        
        
        // register ourselves as a custom scanner check
        callbacks.registerScannerCheck(this);
    }
    
    // helper method to search a response for occurrences of a literal match string
    // and return a list of start/end offsets
    private List<int[]> getMatches(byte[] response, byte[] match)
    {
        List<int[]> matches = new ArrayList<int[]>();

        int start = 0;
        while (start < response.length)
        {
            start = helpers.indexOf(response, match, true, start, response.length);
            if (start == -1)
                break;
            matches.add(new int[] { start, start + match.length });
            start += match.length;
        }
        
        return matches;
    }

    //
    // implement IScannerCheck
    //
    
    @Override
    public List<IScanIssue> doPassiveScan(IHttpRequestResponse baseRequestResponse)
    {
        List<IScanIssue> issues = new ArrayList<>(1);
        //List<IScanIssue> issues = null;
        Iterator it = wf.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            stdout.println(pair.getKey() + " = " + pair.getValue());
            
            // look for matches of our passive check grep string
            List<int[]> matches = getMatches(baseRequestResponse.getResponse(), helpers.stringToBytes(pair.getValue().toString()));
            if (matches.size() > 0)
            {
                // report the issue
                issues.add(new CustomScanIssue(
                        baseRequestResponse.getHttpService(),
                        helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                        new IHttpRequestResponse[] { callbacks.applyMarkers(baseRequestResponse, null, matches) }, 
                        "WAF Detected",
                        "The response contains the string: " + pair.getValue().toString(),
                        "Information"));
                //return issues;
            }
            //else return null;
            
            it.remove(); // avoids a ConcurrentModificationException
        }   
        return issues;
    }

    @Override
    public List<IScanIssue> doActiveScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint)
    {
        /*
        // make a request containing our injection test in the insertion point
        byte[] checkRequest = insertionPoint.buildRequest(INJ_TEST);
        IHttpRequestResponse checkRequestResponse = callbacks.makeHttpRequest(
                baseRequestResponse.getHttpService(), checkRequest);

        // look for matches of our active check grep string
        List<int[]> matches = getMatches(checkRequestResponse.getResponse(), INJ_ERROR);
        if (matches.size() > 0)
        {
            // get the offsets of the payload within the request, for in-UI highlighting
            List<int[]> requestHighlights = new ArrayList<>(1);
            requestHighlights.add(insertionPoint.getPayloadOffsets(INJ_TEST));

            // report the issue
            List<IScanIssue> issues = new ArrayList<>(1);
            issues.add(new CustomScanIssue(
                    baseRequestResponse.getHttpService(),
                    helpers.analyzeRequest(baseRequestResponse).getUrl(), 
                    new IHttpRequestResponse[] { callbacks.applyMarkers(checkRequestResponse, requestHighlights, matches) }, 
                    "Pipe injection",
                    "Submitting a pipe character returned the string: " + helpers.bytesToString(INJ_ERROR),
                    "High"));
            return issues;
        }
        else return null;
        */
        return null;
    }


    @Override
    public int consolidateDuplicateIssues(IScanIssue existingIssue, IScanIssue newIssue)
    {
        // This method is called when multiple issues are reported for the same URL 
        // path by the same extension-provided check. The value we return from this 
        // method determines how/whether Burp consolidates the multiple issues
        // to prevent duplication
        //
        // Since the issue name is sufficient to identify our issues as different,
        // if both issues have the same name, only report the existing issue
        // otherwise report both issues
        if ((existingIssue.getIssueName().equals(newIssue.getIssueName())) &&
            (existingIssue.getUrl().equals(newIssue.getUrl())) &&
            (existingIssue.getIssueDetail().equals(newIssue.getIssueDetail())))
            return -1;
        else return 0;
    }
}

//
// class implementing IScanIssue to hold our custom scan issue details
//
class CustomScanIssue implements IScanIssue
{
    private IHttpService httpService;
    private URL url;
    private IHttpRequestResponse[] httpMessages;
    private String name;
    private String detail;
    private String severity;

    public CustomScanIssue(
            IHttpService httpService,
            URL url, 
            IHttpRequestResponse[] httpMessages, 
            String name,
            String detail,
            String severity)
    {
        this.httpService = httpService;
        this.url = url;
        this.httpMessages = httpMessages;
        this.name = name;
        this.detail = detail;
        this.severity = severity;
    }
    
    @Override
    public URL getUrl()
    {
        return url;
    }

    @Override
    public String getIssueName()
    {
        return name;
    }

    @Override
    public int getIssueType()
    {
        return 0;
    }

    @Override
    public String getSeverity()
    {
        return severity;
    }

    @Override
    public String getConfidence()
    {
        return "Certain";
    }

    @Override
    public String getIssueBackground()
    {
        return null;
    }

    @Override
    public String getRemediationBackground()
    {
        return null;
    }

    @Override
    public String getIssueDetail()
    {
        return detail;
    }

    @Override
    public String getRemediationDetail()
    {
        return null;
    }

    @Override
    public IHttpRequestResponse[] getHttpMessages()
    {
        return httpMessages;
    }

    @Override
    public IHttpService getHttpService()
    {
        return httpService;
    }
    
}

class WafFingerprint{
    private byte[] keyword;
    private String wafType;
    //private String author;
    //private String refUri;
    
    public WafFingerprint(byte[] keyword, String wafType){
        this.keyword = keyword;
        this.wafType = wafType;
    }
}