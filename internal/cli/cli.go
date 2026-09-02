package cli

import (
	"context"
	"flag"
	"fmt"
	"io"
)

type VerifyOptions struct {
	ConfigPath         string
	BaseRef            string
	CandidateRef       string
	EnvFile            string
	ArtifactDir        string
	JSONOutput         string
	JUnitOutput        string
	KeepOnFailure      bool
	Verbose            bool
	NoColor            bool
	AllowUnsafeCompose bool
	AllowRemoteDocker  bool
}

type ValidateOptions struct {
	ConfigPath         string
	BaseRef            string
	CandidateRef       string
	EnvFile            string
	Verbose            bool
	NoColor            bool
	AllowUnsafeCompose bool
}

type DoctorOptions struct {
	ConfigPath         string
	BaseRef            string
	CandidateRef       string
	EnvFile            string
	ArtifactDir        string
	Verbose            bool
	NoColor            bool
	AllowUnsafeCompose bool
	AllowRemoteDocker  bool
}

type Handler interface {
	Verify(context.Context, VerifyOptions) (int, error)
	Validate(context.Context, ValidateOptions) (int, error)
	Doctor(context.Context, DoctorOptions) (int, error)
}

type Streams struct {
	Out io.Writer
	Err io.Writer
}

func Run(ctx context.Context, args []string, streams Streams, version string, handler Handler) (code int) {
	defer func() {
		if recover() != nil {
			fmt.Fprintln(streams.Err, "unexpected internal Backline error")
			code = 24
		}
	}()
	if len(args) == 0 {
		printRootHelp(streams.Out)
		return 0
	}

	switch args[0] {
	case "--help", "-h", "help":
		return runHelp(args[1:], streams)
	case "--version", "version":
		if len(args) != 1 {
			fmt.Fprintf(streams.Err, "%s does not accept arguments\n", args[0])
			return 21
		}
		fmt.Fprintf(streams.Out, "backline %s\n", version)
		return 0
	case "verify":
		return runVerify(ctx, args[1:], streams, handler)
	case "validate":
		return runValidate(ctx, args[1:], streams, handler)
	case "doctor":
		return runDoctor(ctx, args[1:], streams, handler)
	default:
		fmt.Fprintf(streams.Err, "unknown command %q\nRun 'backline help' for usage.\n", args[0])
		return 21
	}
}

func runHelp(args []string, streams Streams) int {
	if len(args) == 0 {
		printRootHelp(streams.Out)
		return 0
	}
	if len(args) > 1 {
		fmt.Fprintln(streams.Err, "help accepts at most one command")
		return 21
	}
	switch args[0] {
	case "verify":
		printVerifyHelp(streams.Out)
	case "validate":
		printValidateHelp(streams.Out)
	case "doctor":
		printDoctorHelp(streams.Out)
	case "version":
		fmt.Fprintln(streams.Out, "Usage: backline version")
	default:
		fmt.Fprintf(streams.Err, "unknown command %q\n", args[0])
		return 21
	}
	return 0
}

func runVerify(ctx context.Context, args []string, streams Streams, handler Handler) int {
	if wantsHelp(args) {
		printVerifyHelp(streams.Out)
		return 0
	}
	var options VerifyOptions
	set := newFlagSet("verify")
	commonVerifyFlags(set, &options)
	if code := parseFlags(set, args, streams); code != 0 {
		return code
	}
	code, err := handler.Verify(ctx, options)
	return finish(code, err, streams)
}

func runValidate(ctx context.Context, args []string, streams Streams, handler Handler) int {
	if wantsHelp(args) {
		printValidateHelp(streams.Out)
		return 0
	}
	options := ValidateOptions{ConfigPath: "backline.yml", CandidateRef: "HEAD"}
	set := newFlagSet("validate")
	set.StringVar(&options.ConfigPath, "config", options.ConfigPath, "repository-relative configuration path")
	set.StringVar(&options.BaseRef, "base-ref", "", "override base Git ref")
	set.StringVar(&options.CandidateRef, "candidate-ref", options.CandidateRef, "candidate Git ref")
	set.StringVar(&options.EnvFile, "env-file", "", "override host-local Compose environment file")
	set.BoolVar(&options.Verbose, "verbose", false, "show detailed validation progress")
	set.BoolVar(&options.NoColor, "no-color", false, "disable ANSI output")
	set.BoolVar(&options.AllowUnsafeCompose, "allow-unsafe-compose", false, "allow otherwise-rejected Compose features")
	if code := parseFlags(set, args, streams); code != 0 {
		return code
	}
	code, err := handler.Validate(ctx, options)
	return finish(code, err, streams)
}

func runDoctor(ctx context.Context, args []string, streams Streams, handler Handler) int {
	if wantsHelp(args) {
		printDoctorHelp(streams.Out)
		return 0
	}
	options := DoctorOptions{ConfigPath: "backline.yml", CandidateRef: "HEAD"}
	set := newFlagSet("doctor")
	set.StringVar(&options.ConfigPath, "config", options.ConfigPath, "repository-relative configuration path")
	set.StringVar(&options.BaseRef, "base-ref", "", "override base Git ref")
	set.StringVar(&options.CandidateRef, "candidate-ref", options.CandidateRef, "candidate Git ref")
	set.StringVar(&options.EnvFile, "env-file", "", "override host-local Compose environment file")
	set.StringVar(&options.ArtifactDir, "artifact-dir", "", "override artifact root directory")
	set.BoolVar(&options.Verbose, "verbose", false, "show detailed prerequisite progress")
	set.BoolVar(&options.NoColor, "no-color", false, "disable ANSI output")
	set.BoolVar(&options.AllowUnsafeCompose, "allow-unsafe-compose", false, "allow otherwise-rejected Compose features")
	set.BoolVar(&options.AllowRemoteDocker, "allow-remote-docker", false, "allow a nonlocal Docker context")
	if code := parseFlags(set, args, streams); code != 0 {
		return code
	}
	code, err := handler.Doctor(ctx, options)
	return finish(code, err, streams)
}

func commonVerifyFlags(set *flag.FlagSet, options *VerifyOptions) {
	options.ConfigPath = "backline.yml"
	options.CandidateRef = "HEAD"
	set.StringVar(&options.ConfigPath, "config", options.ConfigPath, "repository-relative configuration path")
	set.StringVar(&options.BaseRef, "base-ref", "", "override base Git ref")
	set.StringVar(&options.CandidateRef, "candidate-ref", options.CandidateRef, "candidate Git ref")
	set.StringVar(&options.EnvFile, "env-file", "", "override host-local Compose environment file")
	set.StringVar(&options.ArtifactDir, "artifact-dir", "", "override artifact root directory")
	set.StringVar(&options.JSONOutput, "json-output", "", "also write summary JSON to this path")
	set.StringVar(&options.JUnitOutput, "junit-output", "", "write JUnit XML to this path")
	set.BoolVar(&options.KeepOnFailure, "keep-on-failure", false, "retain current-run resources after a non-passing run")
	set.BoolVar(&options.Verbose, "verbose", false, "stream detailed orchestration progress")
	set.BoolVar(&options.NoColor, "no-color", false, "disable ANSI output")
	set.BoolVar(&options.AllowUnsafeCompose, "allow-unsafe-compose", false, "allow otherwise-rejected Compose features")
	set.BoolVar(&options.AllowRemoteDocker, "allow-remote-docker", false, "allow a nonlocal Docker context")
}

func newFlagSet(name string) *flag.FlagSet {
	set := flag.NewFlagSet(name, flag.ContinueOnError)
	set.SetOutput(io.Discard)
	return set
}

func parseFlags(set *flag.FlagSet, args []string, streams Streams) int {
	if err := set.Parse(args); err != nil {
		fmt.Fprintf(streams.Err, "%v\nRun 'backline help %s' for usage.\n", err, set.Name())
		return 21
	}
	if set.NArg() != 0 {
		fmt.Fprintf(streams.Err, "%s does not accept positional arguments\n", set.Name())
		return 21
	}
	return 0
}

func wantsHelp(args []string) bool {
	for _, arg := range args {
		if arg == "--help" || arg == "-h" {
			return true
		}
	}
	return false
}

func finish(code int, err error, streams Streams) int {
	if err != nil {
		fmt.Fprintf(streams.Err, "%v\n", err)
	}
	return code
}

func printRootHelp(out io.Writer) {
	fmt.Fprintln(out, `Backline verifies mixed-version and rollback compatibility for stateful services.

Usage:
  backline verify [flags]
  backline validate [flags]
  backline doctor [flags]
  backline version
  backline help [command]

Run 'backline help <command>' for command-specific flags.`)
}

func printVerifyHelp(out io.Writer) {
	fmt.Fprintln(out, `Usage: backline verify [flags]

Runs the complete baseline, transition, control, coexistence, candidate traffic, and rollback lifecycle.

Flags:
  --config <path>             Candidate-committed configuration (default backline.yml)
  --base-ref <ref>            Override the configured base revision
  --candidate-ref <ref>       Candidate revision (default HEAD)
  --env-file <path>           Override the host-local Compose environment file
  --artifact-dir <path>       Override the run artifact root
  --json-output <path>        Also write summary JSON to this path
  --junit-output <path>       Write JUnit XML to this path
  --keep-on-failure           Retain current-run resources after a non-passing run
  --verbose                   Stream detailed orchestration progress
  --no-color                  Disable ANSI output
  --allow-unsafe-compose      Relax otherwise-blocked Compose validation with a warning
  --allow-remote-docker       Permit a trusted nonlocal Docker endpoint`)
}

func printValidateHelp(out io.Writer) {
	fmt.Fprintln(out, `Usage: backline validate [flags]

Validates the committed candidate configuration and referenced paths without creating Docker resources.

Flags:
  --config <path>             Candidate-committed configuration (default backline.yml)
  --base-ref <ref>            Override the configured base revision
  --candidate-ref <ref>       Candidate revision (default HEAD)
  --env-file <path>           Override the host-local Compose environment file
  --verbose                   Show detailed validation progress
  --no-color                  Disable ANSI output
  --allow-unsafe-compose      Relax otherwise-blocked Compose validation with a warning`)
}

func printDoctorHelp(out io.Writer) {
	fmt.Fprintln(out, `Usage: backline doctor [flags]

Checks Git, Docker, Compose, configuration, paths, executables, and artifact permissions.

Flags:
  --config <path>             Candidate-committed configuration (default backline.yml)
  --base-ref <ref>            Override the configured base revision
  --candidate-ref <ref>       Candidate revision (default HEAD)
  --env-file <path>           Override the host-local Compose environment file
  --artifact-dir <path>       Override the artifact root checked for writability
  --verbose                   Show detailed prerequisite progress
  --no-color                  Disable ANSI output
  --allow-unsafe-compose      Relax otherwise-blocked Compose validation with a warning
  --allow-remote-docker       Permit a trusted nonlocal Docker endpoint`)
}
