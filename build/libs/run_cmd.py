import argparse
import subprocess
import sys

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--command', required=True, help='The command to execute')
    args, unknown = parser.parse_known_args()
    
    try:
        # Windows下使用 shell=True 来支持 dir, del 等内置命令
        process = subprocess.run(args.command, shell=True, capture_output=True, text=True)
        print(process.stdout)
        if process.stderr:
            print("STDERR:", process.stderr)
    except Exception as e:
        print(f"Error executing command: {e}")

if __name__ == "__main__":
    main()