System.setProperty("webdriver.chrome.driver", System.env["CHROMEDRIVER_PATH"] ?: "/home/pmauduit/bin/chromedriver-linux-64bit")

baseUrl = "https://dev-external.gdi-etau.telekom.de"
reportsDir = "target/geb-spock-reports"