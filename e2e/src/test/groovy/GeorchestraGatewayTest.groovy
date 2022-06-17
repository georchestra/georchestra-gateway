import geb.spock.GebSpec

class GeorchestraGatewayConnectionTest extends GebSpec {
    def setup() {
        browser.clearCookies("/")
    }
    def cleanup() {
        browser.clearCookies("/")
    }

    def "I can login using an account from the OpenLDAP"() {
        when: "I click on login"
            withFrame("gdiHeader") {
                $("a.nav-login").click()
            }
        and: "I enter the username/password of the testadmin account"
            // connect as testadmin/testadmin (OpenLDAP)
            $('input#username') << "testadmin"
            $('input#password') << "testadmin"

            $("button[type='submit']").click()
        then: "I am redirected to the landing page being connected"
            withFrame("gdiHeader") {
                $("a.nav-user").displayed
            }
    }

    def "I can login using an account from CIAM"() {
        when: "I click on login"
        withFrame("gdiHeader") {
            $("a.nav-login").click()
        }
        and: "I enter the username/password of the GDIADMIN1.GDI account"
        // connect as testadmin/testadmin (OpenLDAP)
        $('input#username') << "GDIADMIN1.GDI@t-systems.com"
        $('input#password') << "#Start01#Start"

        $("button[type='submit']").click()
        then: "I am redirected to the landing page being connected"
        withFrame("gdiHeader") {
            $("a.nav-user").displayed
        }
    }


}
