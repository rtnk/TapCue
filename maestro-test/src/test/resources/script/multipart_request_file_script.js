// UPLOAD_URL points at a local MockWebServer started by IntegrationTest (Case 136).
const response = http.post(UPLOAD_URL, {
    multipartForm: {
        imagefile: {
            filePath: "../media/abc.png",
        },
        name: "logo"
    }
})

console.log(JSON.stringify(response.body, null, 2));
