package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/ChimdumebiNebolisa/Backline/internal/app"
	"github.com/ChimdumebiNebolisa/Backline/internal/cli"
)

var version = "dev"

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	code := cli.Run(ctx, os.Args[1:], cli.Streams{Out: os.Stdout, Err: os.Stderr}, version, app.New(os.Stdout, version))
	os.Exit(code)
}
