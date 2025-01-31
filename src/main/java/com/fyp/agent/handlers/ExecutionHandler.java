package com.fyp.agent.handlers;

import com.fyp.agent.dbhandlers.AgentDBHandler;
import com.fyp.agent.dbhandlers.ExecutionDBHandler;
import com.fyp.agent.models.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
@Service
public class ExecutionHandler {

    @Autowired
    private Environment env;

    private ExecutionDBHandler executionDBH;

    private AgentDBHandler agentDBH;

    private WebDriver driver;

    public ExecutionHandler(ExecutionDBHandler executionDBH, AgentDBHandler agentDBH) {
        this.executionDBH = executionDBH;
        this.agentDBH = agentDBH;
    }

    public String executeStory(int id) throws MalformedURLException {
        List<TestCase> testCases = executionDBH.getStoryTestCases(id);
        for(TestCase tc : testCases){
            executeTestCase(tc);
        }
        return "excuted";
    }

    public String getTestCaseToExecute(int id) throws MalformedURLException {

        TestCase testCase = executionDBH.getTestCase(id);
        executeTestCase(testCase);
        return "testcase_found";
    }

    private String getDateNow() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    private List<Agent> getAssignedAgents(int storyID) {
        return agentDBH.getAssignedAgents(storyID);
    }

    public String executeTestCase(TestCase testCase) {
            int id = testCase.getId();
            String url = testCase.getuStory().getUrl();

            List<Agent> agents = agentDBH.getAssignedAgents(testCase.getuStory().getId());

            for(int i = 0; i < agents.size();i++){
                if(agents.get(i).getAlive()){
                    try {
                        Agent currAgent = agents.get(i);

                        TestCaseResult tcResult = new TestCaseResult();
                        tcResult.setTestCase(testCase);

                        tcResult.setExecutionTime(getDateNow());

                        tcResult.setExecutionInstance( "ins_"+new Date().getTime());

                        tcResult.setAgent(currAgent);

                        final DesiredCapabilities capability = DesiredCapabilities.chrome();
                        ChromeOptions options = new ChromeOptions();
                        options.addArguments("user-data-dir=D:\\Documents\\IIT\\FinalYear\\FYP\\Implementation\\Misc\\ChromeProfile2");
                        options.addArguments("--start-maximized");
                        capability.setCapability("applicationName", currAgent.getName());
                        capability.setCapability(ChromeOptions.CAPABILITY, options);

                        driver = new RemoteWebDriver(new URL("http://"+env.getProperty("server.address")+":4444/wd/hub"), capability);
                        driver.get(url);

                        tcResult = executionDBH.createTestCaseResult(tcResult);

                        String resultScreenshot = executeTestSteps(id, tcResult, driver);
                        tcResult.setScreenshot(resultScreenshot);
                        if(compareImages(testCase.getScreenshot(),resultScreenshot) >= 80){
                            tcResult.setResult("PASS");
                        } else {
                            tcResult.setResult("FAIL");
                        }
                        if(tcResult.getResult().equalsIgnoreCase(testCase.getExpectedResult())){
                            tcResult.setStatus("PASS");
                        } else {
                            tcResult.setStatus("FAIL");
                        }
                        executionDBH.updateTestCaseResult(tcResult);

                        testCase.setLastExecutedDate(getDateNow());
                        executionDBH.updateTestCase(testCase);
                    } catch (MalformedURLException e) {
                        continue;
                    } finally {
                        driver.quit();
                    }
                }
            }
        return "success";
    }

    private String executeTestSteps(int id, TestCaseResult tcResult, WebDriver driver) {
        List<TestCaseSteps> tcSteps = executionDBH.getTestCaseSteps(id);
        String lastScreenShot = "";
        for (TestCaseSteps st : tcSteps) {
            TestStep step = st.getTestStep();
            UIObject obj = step.getUiObject();
            TestStepResult tsResult = new TestStepResult();
            tsResult.setTestCaseResult(tcResult);
            tsResult.setTestStep(step);
            tsResult.setExecutionTime(getDateNow());

            try {
                WebElement element = driver.findElement(By.xpath(obj.getXpath()));

                switch (step.getStepType()) {
                    case CLICK:
                        System.out.println("Executing step : Click on " + obj.getName());
                        try {
                            element.click();
                        } catch (NoSuchElementException e) {
                            JavascriptExecutor js = (JavascriptExecutor) driver;
                            js.executeScript("arguments[0].click();", element);
                        }
                        break;
                    case TYPE:
                        System.out.println("Executing step : Type " + ((TypeStep) step).getValue() + " on " + obj.getXpath());
                        element.sendKeys(((TypeStep) step).getValue());
                        break;
                    case PRESS:
                        System.out.println("Executing step : Press " + ((PressKeyStep) step).getKeyCode() + " on " + obj.getXpath());
                        element.sendKeys(getKeyFromkeyCode(((PressKeyStep) step).getKeyCode()));
                        break;
                    case SELECT:
                        System.out.println("Executing step : Select " + ((SelectStep) step).getSelectedOption() + " of " + obj.getXpath());
                        Select dropdown = new Select(element);
                        dropdown.selectByVisibleText(((SelectStep) step).getSelectedOption());
                        break;
                }

                String base64 = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BASE64);
                tsResult.setScreenshot(base64);
                executionDBH.createTestStepResult(tsResult);
                lastScreenShot = base64;
//                System.out.println(base64);

            } catch (ElementNotVisibleException e) {
                System.out.println("Couldnt find the element!. Step failed, moving onto the next step.");
                continue;
            }
        }

        return lastScreenShot;
    }

    private Double compareImages(String img1, String img2) {
        HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead

        JSONArray array = new JSONArray();
        array.put(img1);
        array.put(img2);

        try {

            HttpPost request = new HttpPost("http://"+env.getProperty("server.address")+":5000/compare_images");
            StringEntity requestEntity = new StringEntity(
                    array.toString(),
                    ContentType.APPLICATION_JSON);
            request.addHeader("Content-Type", "application/json");
            request.setEntity(requestEntity);
            HttpResponse response = httpClient.execute(request);

            String responseJSON = EntityUtils.toString(response.getEntity());
            JSONObject result = new JSONObject(responseJSON);

            return result.getDouble("rate");

        }catch (Exception ex) {
            System.out.println("here");
        }
        return -9999.999;
    }

    private Keys getKeyFromkeyCode(int keyCode) {
        switch (keyCode) {
            case 8:
                return Keys.BACK_SPACE;
            case 9:
                return Keys.TAB;
            case 13:
                return Keys.ENTER;
            case 16:
                return Keys.SHIFT;
            case 17:
                return Keys.CONTROL;
        }
        return null;
    }

}
