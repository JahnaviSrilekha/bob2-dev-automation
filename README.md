# bob2-dev-automation
Development automation trial
This project simplifies the developement process. 
Old flow:
1. Define product requirements and discuss with product for clarification.
2. Discuss with technical architect to check feasinility and define architectural diagrams and define functionality.
3. Go through multiple refinement steps.
4. Once tasks are clear after refinement then assign it to developer.
5. Developer need to understand both functional, technical and non functional requirements
6. Code the changes. 
7. Test the changes.
8. Go through multiple reviews and repeat code and test in case of valid rework.
9. Then ship code to dev environment.

New flow:
1. Automated task plan with product and technical architect and tester together. Taks and tests are prepared togther. This needs 3 specialist agents. Output should contain, SRS, DDS
2. Automated development where code , review run in sequence for each layer. Coding is done only when review is success. This needs coding agents and peer coding agents
3. Predefined tests are executed now on the written code.
4. On success the changes are merged and are ready for deployment.

