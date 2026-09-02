package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

func main() {
	if len(os.Args) < 2 {
		fail("usage: workload <leak|write|read> [arguments]")
	}
	if os.Args[1] == "leak" {
		fmt.Println(os.Getenv("DEMO_SECRET"))
		return
	}
	if os.Args[1] == "handoff-write" {
		if err := os.WriteFile(os.Getenv("BACKLINE_SHARED_DIR")+string(os.PathSeparator)+"handoff.txt", []byte("candidate-only-complete\n"), 0o600); err != nil {
			fail(err.Error())
		}
		return
	}
	if os.Args[1] == "handoff-read" {
		contents, err := os.ReadFile(os.Getenv("BACKLINE_SHARED_DIR") + string(os.PathSeparator) + "handoff.txt")
		if err != nil || strings.TrimSpace(string(contents)) != "candidate-only-complete" {
			fail(fmt.Sprintf("handoff unavailable: %v %q", err, contents))
		}
		return
	}
	if os.Args[1] == "fail" {
		fail("intentional candidate-only failure")
	}
	if len(os.Args) != 4 {
		fail("write/read requires item and status")
	}
	base := strings.TrimRight(os.Getenv("BACKLINE_TARGET_URL"), "/")
	client := &http.Client{Timeout: 5 * time.Second}
	var request *http.Request
	var err error
	if os.Args[1] == "write" {
		request, err = http.NewRequest(http.MethodPost, base+"/items/"+os.Args[2]+"?status="+os.Args[3], nil)
	} else {
		request, err = http.NewRequest(http.MethodGet, base+"/items/"+os.Args[2], nil)
	}
	if err != nil {
		fail(err.Error())
	}
	response, err := client.Do(request)
	if err != nil {
		fail(err.Error())
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(response.Body)
	if os.Args[1] == "write" && response.StatusCode != http.StatusNoContent {
		fail(fmt.Sprintf("write returned %d: %s", response.StatusCode, body))
	}
	if os.Args[1] == "read" && (response.StatusCode != http.StatusOK || strings.TrimSpace(string(body)) != os.Args[3]) {
		fail(fmt.Sprintf("read returned %d %q, expected %q", response.StatusCode, strings.TrimSpace(string(body)), os.Args[3]))
	}
}

func fail(message string) {
	fmt.Fprintln(os.Stderr, message)
	os.Exit(1)
}
