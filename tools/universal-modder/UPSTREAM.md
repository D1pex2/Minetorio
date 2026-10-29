# Vendored Universal Modder

Source: https://github.com/rehan-remade/universal-modder
Version: 0.2.0
Commit: c33afda71b5fd55963001642266fdc3c90d0f589
License: MIT (see LICENSE).

This project vendors upstream skills in ../../.agents/skills/universal-modder-* and the CLI/source knowledge base here. Skill bodies and CLI sources are unmodified. Plugin manifests, automatic MCP connections, hooks, upstream media and example projects are not installed. See upstream for examples linked by skills. This is a project-local skill/tool bundle, not a global Codex plugin installation.

Optional isolated CLI setup from the Minetorio root (Python 3.10+):

```powershell
python -m venv tools/universal-modder/.venv
& tools/universal-modder/.venv/Scripts/python.exe -m pip install -e ./tools/universal-modder
& tools/universal-modder/.venv/Scripts/um.exe --help
```

The venv and Python dependencies are local to this project, ignored by Git and recreated on another computer. Skills themselves require no pip installation. Some CLI actions require external tools (e.g. Blender/FFmpeg), services or credentials; only configure those when needed. Do not put credentials in Git.
