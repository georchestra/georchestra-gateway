fetch("/style-config").then(res => {
    if (res.ok) {
        return res.json();
    }
    throw new Error("Gateway down")
}).then(json => {
    if (json.stylesheet){
        var styleSheet = document.createElement("style")
        styleSheet.textContent = json.stylesheet
        document.head.appendChild(styleSheet)
    }

})